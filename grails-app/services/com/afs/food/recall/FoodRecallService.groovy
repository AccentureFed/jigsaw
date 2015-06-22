package com.afs.food.recall

import grails.transaction.Transactional
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import org.codehaus.groovy.grails.web.json.JSONObject

@Transactional
class FoodRecallService {

    /**
     * The maximum amount of results to return from the FDA Food service.<br /><br />
     *
     * Note: The API will not support returning more than 100, do not set above 100
     */
    private static final def MAX_RESULTS = 100
    private static final def BASE_URL = "https://api.fda.gov/food/enforcement.json?limit=${MAX_RESULTS}"

    def stateNormalizationService
	LocalDate lastNotified = LocalDate.now().minusMonths(2)

    /**
     * Returns the first {@link #MAX_RESULTS} recalls from the FDA Service API.  The data is passed back as a {@link JSONObject}.<br /><br />
     *
     * Each return will have an added JSON Array field called 'normalized_distribution_pattern' that will contain all of the state codes each recall was distributed in.
     * @return A {@link JSONObject} representing the recalls.
     */
    def getRecalls() {
        def json = new JSONObject(new URL("${BASE_URL}").getText())

        final Set<String> distributionStates = []
        json.results.each { result ->
            // try to find the states in the natural language value and add it to the result
            result.normalized_distribution_pattern = stateNormalizationService.getStates(result.distribution_pattern)*.getAbbreviation()
        }

        return json
    }
	
	def needUpdate() {
		def json = new JSONObject(new URL("https://api.fda.gov/food/enforcement.json").getText())
		LocalDate lastUpdate = LocalDate.parse(json.meta.last_updated, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
		return (lastUpdate > lastNotified)
	}
	
	def sendNotifications(){
		println("Last notified: ${lastNotified}")
		if(needUpdate()){
			println("Sending notifications")
			def json = new JSONObject(new URL("https://api.fda.gov/food/enforcement.json?search=recall_initiation_date:[${lastNotified}+TO+${LocalDate.now()}]&limit=100").getText())
			final Set<String> distributionStates = []
			json.results.each { result ->
				println("${result.recall_number} - ${result.recall_initiation_date}")
			}
			lastNotified = LocalDate.parse(json.meta.last_updated, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
		}
	}
	
	def readRss(){
		def url = "http://www2c.cdc.gov/podcasts/createrss.asp?c=146"
		def rss = new XmlSlurper().parse(url)
		println rss.channel.title
		rss.channel.item.each { item->
			println "- ${item.title}" 
		}​
	}
}
