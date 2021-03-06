<!DOCTYPE html>
<!--[if lt IE 7]>      <html class="no-js lt-ie9 lt-ie8 lt-ie7"  ng-app="jigsawApp"> <![endif]-->
<!--[if IE 7]>         <html class="no-js lt-ie9 lt-ie8" ng-app="jigsawApp"> <![endif]-->
<!--[if IE 8]>         <html class="no-js lt-ie9" ng-app="jigsawApp"> <![endif]-->
<!--[if gt IE 8]><!--> <html class="no-js" ng-app="jigsawApp"> <!--<![endif]-->
	<head>
		<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
		<meta http-equiv="X-UA-Compatible" content="IE=edge,chrome=1">
		<title>FDA Food Recalls</title>
		<meta name="viewport" content="width=device-width, initial-scale=1.0">
		
  		<asset:stylesheet href="application.css"/>
  		<asset:javascript src="application.js"/>

		<asset:link rel="shortcut icon" href="icon_fda.png" type="image/x-icon"/>
		<g:layoutHead/>
	</head>
	<body>
        <!--[if lt IE 10]>
            <p class="browserupgrade">You are using an <strong>outdated</strong> browser. Please <a href="http://browsehappy.com/">upgrade your browser</a> to improve your experience.</p>
        <![endif]-->
        <div class="container-fluid">
        	<div class="row">
				<div id="content" class="col-md-10 col-md-offset-1">
			        <div ui-view="navbar" ng-cloak></div>
			        <div id="mainContent" class="well" ui-view="content"></div>
			        <div ui-view="footer"></div>
			    </div>
			</div>
		</div>
	</body>
</html>
