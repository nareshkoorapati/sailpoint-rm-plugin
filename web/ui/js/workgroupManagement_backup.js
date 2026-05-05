/**
 * Workgroup Management — standalone Angular module (WorkgroupApp).
 * Listed as a dependency of RoleApp in roleManagement.js so its controller is
 * available under the same ng-app bootstrap as RoleController.
 */

//var WorkgroupApp = angular.module('WorkgroupApp', ['ui.bootstrap', 'sailpoint.comment', 'sailpoint.util', 'sailpoint.config', 'sailpoint.identity.account']);

RoleApp.controller('WorkgroupManagementController', ['$scope', '$http','$timeout', '$q', function ($scope, $http, $timeout, $q) {
		// Fetch plugin config immediately after controller initializes
		console.log('Initializing WorkgroupManagementController and fetching plugin config');
		const csrfToken = PluginHelper.getCsrfToken();
		if (csrfToken) {
			$http.defaults.headers.common['X-XSRF-TOKEN'] = csrfToken;
		}
		$scope.headerToken = {
			headers : {
				'X-XSRF-TOKEN' : 'sdasd12312312asdas'
			}
		};

		
		$scope.workgroupPageSize = 25;
		$scope.workgroupCurrentPage = 1;
		$scope.workgroupTotal = 0;
		$scope.workgroupRows = [];
		$scope.workgroupSearchText = '';
		$scope.showWgMembersModal = false;
		$scope.wgMembers = [];
		$scope.wgMemberHeaders = [];
		$scope.wgMembersTitle = '';
		$scope.wgMembersLoading = false;
		$scope.wgLoading = false;

		$scope.workgroupStartDisplay = function () {
			if (!$scope.workgroupTotal) {
				return 0;
			}
			return ($scope.workgroupCurrentPage - 1) * $scope.workgroupPageSize + 1;
		};

		$scope.workgroupEndDisplay = function () {
			return Math.min(
				$scope.workgroupCurrentPage * $scope.workgroupPageSize,
				$scope.workgroupTotal
			);
		};

		$scope.loadWorkgroups = function () {
			const start = ($scope.workgroupCurrentPage - 1) * $scope.workgroupPageSize;
			const params = {
				start: start,
				limit: $scope.workgroupPageSize,
				sort: 'name',
				dir: 'ASC'
			};
			const q = ($scope.workgroupSearchText || '').trim();
			if (q.length > 0) {
				params.query = q;
			}
			$scope.wgLoading = true;
			$http({
				method: 'GET',
				url: PluginHelper.getPluginRestUrl('rolemanagement/workgroups'),
				params: params,
				headers: $scope.headerToken.headers
			}).then(
				function success(res) {
					const data = res.data || {};
					$scope.workgroupRows = data.objects || [];
					$scope.workgroupTotal = data.total != null ? data.total : 0;
					$scope.wgLoading = false;
				},
				function error() {
					$scope.workgroupRows = [];
					$scope.workgroupTotal = 0;
					$scope.wgLoading = false;
					$scope.showToast('Failed to load workgroups', 'error');
				}
			);
		};

		$scope.triggerWorkgroupSearch = function () {
			$scope.workgroupCurrentPage = 1;
			$scope.loadWorkgroups();
		};

		$scope.wgGoToFirstPage = function () {
			$scope.workgroupCurrentPage = 1;
			$scope.loadWorkgroups();
		};

		$scope.wgGoToPreviousPage = function () {
			if ($scope.workgroupCurrentPage > 1) {
				$scope.workgroupCurrentPage--;
				$scope.loadWorkgroups();
			}
		};

		$scope.wgGoToNextPage = function () {
			const maxPage = Math.max(1, Math.ceil($scope.workgroupTotal / $scope.workgroupPageSize));
			if ($scope.workgroupCurrentPage < maxPage) {
				$scope.workgroupCurrentPage++;
				$scope.loadWorkgroups();
			}
		};

		$scope.wgGoToLastPage = function () {
			const maxPage = Math.max(1, Math.ceil($scope.workgroupTotal / $scope.workgroupPageSize));
			$scope.workgroupCurrentPage = maxPage;
			$scope.loadWorkgroups();
		};

		$scope.wgChangePageSize = function () {
			$scope.workgroupCurrentPage = 1;
			$scope.loadWorkgroups();
		};

		$scope.openWorkgroupMembers = function (wg) {
			if (!wg || !wg.id) {
				return;
			}
			$scope.wgMembersTitle = wg.displayName || wg.name || wg.id;
			$scope.showWgMembersModal = true;
			$scope.wgMembersLoading = true;
			$scope.wgMembers = [];
			$scope.wgMemberHeaders = [];
			$http({
				method: 'GET',
				url: PluginHelper.getPluginRestUrl('rolemanagement/workgroup/' + encodeURIComponent(wg.id)),
				headers: $scope.headerToken.headers
			}).then(
				function success(res) {
					const d = res.data || {};
					$scope.wgMembers = d.objects || [];
					$scope.wgMemberHeaders =
						d.headers && d.headers.length
							? d.headers
							: [
									{ key: 'name', label: 'Name' },
									{ key: 'displayName', label: 'Display Name' },
									{ key: 'employeeid', label: 'Network Id' }
								];
					$scope.wgMembersLoading = false;
				},
				function error() {
					$scope.wgMembersLoading = false;
					$scope.showToast('Could not load workgroup members', 'error');
				}
			);
		};

		$scope.closeWgMembersModal = function () {
			$scope.showWgMembersModal = false;
		};

		$scope.loadWorkgroups();
		


	}]);


	/*jQuery(document).on('click', '#workgroupMenuItem', function () {
		console.log('Workgroup menu item clicked, switching to Workgroup section');
		var el = document.querySelector('[ng-controller="WorkgroupManagementController"]');
		if (!el) {
			return;
		}
		var scope = angular.element(el).scope();
		if (!scope) {
			return;
		}
		scope.$apply(function () {
			scope.setSection('workgroup');
		});
	});*/

