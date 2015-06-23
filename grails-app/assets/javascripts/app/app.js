'use strict';

angular.module('jigsawApp', ['LocalStorageModule', 'tmh.dynamicLocale',
    'ngResource', 'ui.router', 'ngCookies', 'ngCacheBuster', 'infinite-scroll', 'angular-ladda'])

    .run(function ($rootScope, $location, $window, $http, $state, ENV, VERSION) {
        $rootScope.ENV = ENV;
        $rootScope.VERSION = VERSION;
        $rootScope.$on('$stateChangeStart', function (event, toState, toStateParams) {
            $rootScope.toState = toState;
            $rootScope.toStateParams = toStateParams;
        });

        $rootScope.$on('$stateChangeSuccess',  function(event, toState, toParams, fromState, fromParams) {


            $rootScope.previousStateName = fromState.name;
            $rootScope.previousStateParams = fromParams;

        });

        $rootScope.back = function() {
            if ($state.get($rootScope.previousStateName) === null) {
                $state.go('home');
            } else {
                $state.go($rootScope.previousStateName, $rootScope.previousStateParams);
            }
        };
    })

    .config(function ($stateProvider, $urlRouterProvider, $httpProvider, $locationProvider, httpRequestInterceptorCacheBusterProvider) {

        //enable CSRF
        $httpProvider.defaults.xsrfCookieName = 'CSRF-TOKEN';
        $httpProvider.defaults.xsrfHeaderName = 'X-CSRF-TOKEN';

        //Cache everything except rest api requests
        httpRequestInterceptorCacheBusterProvider.setMatchlist([/.*api.*/, /.*protected.*/], true);

        $urlRouterProvider.otherwise('/');
        $stateProvider.state('site', {
            'abstract': true,
            views: {
                'navbar@': {
                    templateUrl: 'components/navbar/navbar.html',
                    controller: 'NavbarController'
                },
                'content@': {
                    templateUrl: 'app/main/main.html',
                    controller: 'MainController'
                }
            }
        });


    })
    .config(function (laddaProvider){
    	laddaProvider.setOption({
    		style: 'expand-left'
    	});
    });
