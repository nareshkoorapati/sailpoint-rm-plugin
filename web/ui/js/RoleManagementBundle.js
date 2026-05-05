//'use strict';
(function() {


	function RoleManagementDirectiveCtrl($q, RoleManagementService) {

		var me = this;
		this.message=undefined;
		
		this.initialize=function(){
			RoleManagementService.getContent().then(function(result) {
				console.log("result="+result.data);
				me.message=result.data;
			})
		};
	    
	    this.initialize();
	}
	
	RoleManagementDirectiveCtrl.$inject = ['$q', 'RoleManagementService'];
	var widgetFunction = function() {
	    angular.module('sailpoint.home.desktop.app')
	    .service('RoleManagementService', ['SP_CONTEXT_PATH', '$http', function(SP_CONTEXT_PATH, $http) {

		    this.getContent = function() {
		        return $http.get(SP_CONTEXT_PATH + '/plugin/rest/RoleManagement/message');
		    };
		
		}])
		.controller('RoleManagementDirectiveCtrl', RoleManagementDirectiveCtrl)
		.directive('spRoleManagementWidget', function() {
			   //console.log("Directive");
			    return {
			        restrict: 'E',
			        scope: {
			            widget: '=spWidget'
			        },
			        controller: 'RoleManagementDirectiveCtrl',
			        controllerAs: 'RoleManagementCtrl',
			        bindToController: true,

			        template:
			            '<div class="seri-widget" sp-loading-mask="RoleManagementCtrl.message">' +
			            '  <div class="panel-body" >' +
			            '    <div>Widget says {{ RoleManagementCtrl.message }} !!</div>'+
			            '  </div>' +
			            '  <div class="panel-footer">A footer' +
			            '  </div>' +
			            '</div>'
			    };
		});

	};
	PluginHelper.addWidgetFunction(widgetFunction);
})();