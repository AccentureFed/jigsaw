package com.afs.food.recall

import grails.transaction.NotTransactional
import grails.transaction.Transactional

import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.concurrent.TimeUnit

import org.codehaus.groovy.grails.web.json.JSONObject

import com.afs.jigsaw.fda.food.api.*
import com.google.common.base.Preconditions

class FoodRecallService {

    /**
     * There is no data in the API before 2012
     */
    private static def START_YEAR = 2012

    /**
     * This is the format for the API
     */
    private static def INPUT_DATE_FORMAT = 'yyyyMMdd'

    /**
     * Better readability date format
     */
    private static def DISPLAY_DATE_FORMAT = 'MM/dd/yy'

    /**
     * The maximum amount of results to return from the FDA Food service.<br /><br />
     *
     * Note: The API will not support returning more than 100, do not set above 100
     */
    private static final def MAX_RESULTS = 100

    private static final def BASE_URL = "https://api.fda.gov/food/enforcement.json"

    def stateNormalizationService
    def barcodeNormalizationService
    def sessionFactory

    /**
     * Fetches recalls from the API for the given year and offset. Results will be given at {@link #MAX_RESULTS} at a time.
     * @param year The year to get recalls for. Must be >= {@link #START_YEAR}
     * @param offset The record offset to start at. Must be >=0.
     * @return A {@link JSONObject} representing the recalls. Each recall result will have an added JSON Array field called
     * 'normalized_distribution_pattern' that will contain all of the state codes each recall was distributed in AND a 'normalized_barcodes'
     * field that will contain a list of all the upc barcodes.
     */
    @NotTransactional
    def fetchRecallsFromApi(final int year, final int offset) {
        Preconditions.checkArgument(year >= START_YEAR, 'Year must be >= the START_YEAR (2012)')
        Preconditions.checkArgument(offset >=0, 'The offset must be >= 0')

        def json = new JSONObject(new URL("${BASE_URL}?limit=${MAX_RESULTS}&skip=${offset}&search=report_date:[${year}0101+TO+${year}1231]").getText())

        def inputFormat = new SimpleDateFormat(INPUT_DATE_FORMAT)
        final Set<String> distributionStates = []
        json.results.each { result ->
            // reformat the dates to easier to an easier to read format
            result.report_date = inputFormat.parse(result.report_date).format(DISPLAY_DATE_FORMAT)
            result.recall_initiation_date = inputFormat.parse(result.recall_initiation_date).format(DISPLAY_DATE_FORMAT)

            // try to find the states in the natural language value and add it to the result AND find UPC barcodes
            def distributionPattern = result.distribution_pattern
            def productDescription = result.product_description
            def codeInfo = result.code_info

            result.normalized_barcodes = barcodeNormalizationService.getUPCBarcodeNumbers("${productDescription} ${codeInfo}")

            if(distributionPattern.toLowerCase().contains('on site retail')) {
                // this was distributed at site in the state where it is made
                distributionPattern = "${result.distribution_pattern} ${result.state}"
            }
            result.normalized_distribution_pattern = stateNormalizationService.getStates(distributionPattern)*.getAbbreviation()
        }

        return json
    }

    /**
     * Gets the last date (in the API's string format) that the API was updated with data.
     * @return A date string representing the last time the API was updated.
     */
    @NotTransactional
    def fetchLastUpdatedDate() {
        def json = new JSONObject(new URL(BASE_URL).getText())
        return json.meta.last_updated
    }

    /**
     * Deletes all data in the local database cache and re-populates it with the data from the API.
     */
    @Transactional
    def updateLocalCache() {
        def start = System.nanoTime()

        // delete all records before updating
        FoodRecall.executeUpdate('delete from FoodRecall')
        RecallState.executeUpdate('delete from RecallState')

        // load all recalls, enrich the data and cache them locally

        // the date was already normalized to the display date when it was fetched
        def dateFormatter = new SimpleDateFormat(DISPLAY_DATE_FORMAT)

        /**
         * We can only pull 5,000 items at a time, so pull by year to get all recalls
         */
        def currYear = LocalDate.now().year
        for(int year = START_YEAR; year <= currYear; year++) {
            // get first batch
            def skip = 0
            def json

            def total = fetchRecallsFromApi(year, skip).meta.results.total.toInteger()

            // cache the enriched data to the database
            while(skip < total) {
                // get next batch
                json = fetchRecallsFromApi(year, skip)

                json.results.each { result ->
                    def foodRecall = new FoodRecall()
                    foodRecall.enrichedJSONPayload = result.toString()
                    foodRecall.reportDate = dateFormatter.parse(result.report_date)
                    foodRecall.recallNumber = result.recall_number
                    foodRecall.severity = Severity.getByFdaValue(result.classification)
                    foodRecall.productDescription = result.product_description
                    foodRecall.recallingFirm = result.recalling_firm
                    foodRecall.reasonForRecall = result.reason_for_recall
                    foodRecall.barcodes = result.normalized_barcodes.join(' ')

                    result.normalized_distribution_pattern.each { stateAbbreviation ->
                        def state = State.fromString(stateAbbreviation)
                        def recallState = RecallState.findByState(state) ?: new RecallState()
                        recallState.state = state

                        foodRecall.addToDistributionStates(recallState)
                    }

                    if(!foodRecall.save()) {
                        log.warn("Error saving food recall, most likely a duplicate recall number of ${result.recall_number}")
                    }
                }

                skip += json.results.size()

                // flush after every bulk
                sessionFactory.getCurrentSession().flush()
            }
        }

        def end = System.nanoTime()
        log.debug("Took ${TimeUnit.NANOSECONDS.toSeconds(end-start)} seconds to cache ${FoodRecall.count()} recalls")
    }

    /**
     * Gets the count of recalls grouped by severity for the given parameters.  All parameters are optional.
     * @param state The state to get counts for. If no state is given, then nationwide counts are returned.
     * @param start The start date to get counts from. If not given, then the counts will be from the beginning of time.
     * @param end The end date to get counts up to. If not given, then the counts will go until current.
     * @return A list of lists, each list represents the severity as the 1st element and count as the 2nd, ex: [['HIGH', 3757], ['LOW', 269], ['MEDIUM', 3779]]
     */
    @Transactional(readOnly = true)
    def getCountsByState(final State state, final Date start, final Date end) {
        return FoodRecall.withCriteria {
            projections {
                groupProperty('severity')
                countDistinct 'recallNumber'
            }

            if(start) {
                ge('reportDate', start)
            }

            if(end) {
                le('reportDate', end)
            }

            if(state) {
                distributionStates { 'in'('state', state) }
            }
        }
    }

    /**
     * Gets the recalls for the given search criteria.  All fields search fields are optional (state, upc, and start/end dates).
     * Fields that are given are 'AND'ed together.  The results will be ordered by the {@link FoodRecall#reportDate} descending field.
     * @param state The state to get recalls for. If not given, all recalls will be given.
     * @param searchText Any free-form text to me matched with a UPC (exact match), Product Description (substring match),
     * Recalling Firm (substring match), or Reason for Recall (substring match). If not given, ignored.
     * @param start The start date to get recalls from. If not given, then the recalls will be from the beginning of time.
     * @param end The end date to get recalls up to. If not given, then the recalls will go until current.
     * @param max The max amount of recalls to get at once. Must be > 0
     * @param offset The offset to get the results starting from. Must be >= 0
     * @return A list of {@link FoodRecall} recalls that matched the given query, ordered by {@link FoodRecall#reportDate} descending.
     */
    @Transactional(readOnly = true)
    def getRecalls(final State state, final String searchText, final Date start, final Date end, final int max, final int offset) {
        Preconditions.checkArgument(max > 0, 'The max to get must be > 0')
        Preconditions.checkArgument(offset >=0, 'The offset must be >= 0')

        def crit = FoodRecall.createCriteria()
        return crit.list(max: max, offset: offset) {

            if(state) {
                distributionStates { 'in'('state', state) }
            }

            if(searchText) {
                or {
                    ilike('barcodes', "%${searchText}%")
                    ilike('productDescription', "%${searchText}%")
                    ilike('recallingFirm', "%${searchText}%")
                    ilike('reasonForRecall', "%${searchText}%")
                }
            }

            if(start) {
                ge('reportDate', start)
            }

            if(end) {
                le('reportDate', end)
            }

            order('reportDate', 'desc')
            order('recallNumber', 'desc')
        }
    }

}
