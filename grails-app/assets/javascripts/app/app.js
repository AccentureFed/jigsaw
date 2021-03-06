'use strict';

angular.module('jigsawApp', ['http-auth-interceptor', 'LocalStorageModule', 'tmh.dynamicLocale',
    'ngResource', 'ui.router', 'ngCookies', 'ngCacheBuster', 'datamaps'])

    .run(function ($rootScope, $location, $window, $http, $state, ENV) {
        $rootScope.ENV = ENV;
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

        $httpProvider.defaults.xsrfCookieName = 'CSRF-TOKEN';
        $httpProvider.defaults.xsrfHeaderName = 'X-CSRF-TOKEN';

        httpRequestInterceptorCacheBusterProvider.setMatchlist([/.*api.*/, /.*protected.*/], true);

        $urlRouterProvider.otherwise('/');
        $stateProvider.state('site', {
            'abstract': true,
            views: {
                'navbar@': {
                    templateUrl: 'app/navbar/navbar.html',
                    controller: 'NavbarController'
                },
                'content@': {
                    templateUrl: 'app/main/main.html',
                    controller: 'MainController'
                },
                'footer@': {
                    templateUrl: 'app/footer/footer.html',
                    controller: 'FooterController'
                }
            }
        });


    });

function getAuthModuleHttpConfig() {
    return {
        ignoreAuthModule: true
    };
}

function getLocalBrowserToken() {
    return sessionStorage["authToken"];
 }

 function setLocalBrowserToken(value) {
     sessionStorage["authToken"] = value;
 }

 function getHttpAuthConfig() {
     return {
         headers: {
             'X-Auth-Token': getLocalBrowserToken()
         }
     };
 }