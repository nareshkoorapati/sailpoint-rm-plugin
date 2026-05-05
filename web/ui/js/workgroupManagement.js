/**
 * Workgroup Management — module WorkgroupApp (own services/controllers namespace).
 * ng-app is RoleApp, so the UI controller must also be registered on RoleApp
 * (submodule-only registration is not reliable for ng-app="RoleApp" in all IIQ builds).
 *
 * Load order: roleManagement.js (creates RoleApp) → this file → bulkRequest.js.
 */
(function () {
	'use strict';

	var WorkgroupApp = angular.module('WorkgroupApp', []);

	var workgroupManagementController = [
		'$scope',
		'$http',
		function ($scope, $http) {
			const csrfToken = PluginHelper.getCsrfToken();
			if (csrfToken) {
				$http.defaults.headers.common['X-XSRF-TOKEN'] = csrfToken;
			}
			$scope.headerToken = {
				headers: {
					'X-XSRF-TOKEN': csrfToken || ''
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
			$scope.wgMemberSearch = '';
			$scope.wgMemberPageSize = 10;
			$scope.wgMemberCurrentPage = 1;
			$scope.wgMemberTotalPages = 1;
			$scope.wgMembersActiveTab = 'members';
			$scope.selectedWgId = null;
			$scope.wgOwnerRoles = [];
			$scope.wgOwnerRoleHeaders = [];
			$scope.wgOwnerRolesLoading = false;
			$scope.wgOwnerRoleSearch = '';
			$scope.wgOwnerRolePageSize = 10;
			$scope.wgOwnerRoleCurrentPage = 1;
			$scope.wgOwnerRoleTotalPages = 1;

			$scope.wgStats = {};
			$scope.wgStatsLoading = true;

			$scope.wgOwnershipDonutStyle = function () {
				var t = $scope.wgStats.totalWorkgroups || 0;
				var o = $scope.wgStats.workgroupsOwningRoles || 0;
				if (t === 0) {
					return { background: '#e8ecef' };
				}
				var deg = (o / t) * 360;
				return {
					background:
						'conic-gradient(#10A2CE 0deg ' +
						deg +
						'deg, #d8dee4 ' +
						deg +
						'deg 360deg)'
				};
			};

			$scope.wgStatusDonutStyle = function () {
				var t = $scope.wgStats.totalWorkgroups || 0;
				var a = $scope.wgStats.activeWorkgroups || 0;
				if (t === 0) {
					return { background: '#e8ecef' };
				}
				var deg = (a / t) * 360;
				return {
					background:
						'conic-gradient(#2e7d32 0deg ' +
						deg +
						'deg, #c62828 ' +
						deg +
						'deg 360deg)'
				};
			};

			$scope.wgOwnershipBarStyle = function () {
				var t = $scope.wgStats.totalWorkgroups || 0;
				var o = $scope.wgStats.workgroupsOwningRoles || 0;
				var pct = t > 0 ? (o / t) * 100 : 0;
				return { width: pct + '%' };
			};

			$scope.wgNotOwnerBarStyle = function () {
				var t = $scope.wgStats.totalWorkgroups || 0;
				var n = $scope.wgStats.workgroupsNotOwningRoles || 0;
				var pct = t > 0 ? (n / t) * 100 : 0;
				return { width: pct + '%' };
			};

			$scope.wgOwnershipPercent = function () {
				var t = $scope.wgStats.totalWorkgroups || 0;
				var o = $scope.wgStats.workgroupsOwningRoles || 0;
				if (t === 0) {
					return 0;
				}
				return Math.round((o / t) * 1000) / 10;
			};

			$scope.wgNotOwnerPercent = function () {
				var t = $scope.wgStats.totalWorkgroups || 0;
				var n = $scope.wgStats.workgroupsNotOwningRoles || 0;
				if (t === 0) {
					return 0;
				}
				return Math.round((n / t) * 1000) / 10;
			};

			$scope.loadWgStats = function () {
				$scope.wgStatsLoading = true;
				$http({
					method: 'GET',
					url: PluginHelper.getPluginRestUrl('rolemanagement/workgroups/stats'),
					headers: $scope.headerToken.headers
				}).then(
					function success(res) {
						$scope.wgStats = res.data || {};
						$scope.wgStatsLoading = false;
					},
					function error() {
						$scope.wgStatsLoading = false;
						$scope.wgStats = {
							totalWorkgroups: 0,
							workgroupsOwningRoles: 0,
							workgroupsNotOwningRoles: 0,
							activeWorkgroups: 0,
							disabledWorkgroups: 0,
							rolesWithWorkgroupOwner: 0
						};
						$scope.showToast('Could not load workgroup statistics', 'error');
					}
				);
			};

			$scope.getWgMemberSearchKeys = function () {
				if (!$scope.wgMemberHeaders || !$scope.wgMemberHeaders.length) {
					return ['name', 'displayName', 'employeeid'];
				}
				return $scope.wgMemberHeaders.map(function (h) {
					return h.key;
				});
			};

			$scope.filteredWgMembers = function () {
				if (!Array.isArray($scope.wgMembers)) {
					return [];
				}
				const keyword = ($scope.wgMemberSearch || '').toLowerCase().trim();
				if (!keyword) {
					return $scope.wgMembers;
				}
				const keys = $scope.getWgMemberSearchKeys();
				return $scope.wgMembers.filter(function (member) {
					return keys.some(function (key) {
						const val = member[key];
						return val != null && String(val).toLowerCase().indexOf(keyword) !== -1;
					});
				});
			};

			$scope.updateWgMembersPagination = function () {
				const totalItems = $scope.filteredWgMembers().length;
				const size = Number($scope.wgMemberPageSize) || 10;
				$scope.wgMemberTotalPages = Math.max(1, Math.ceil(totalItems / size) || 1);
				if ($scope.wgMemberCurrentPage > $scope.wgMemberTotalPages) {
					$scope.wgMemberCurrentPage = $scope.wgMemberTotalPages;
				}
				if ($scope.wgMemberCurrentPage < 1) {
					$scope.wgMemberCurrentPage = 1;
				}
			};

			$scope.paginatedWgMembers = function () {
				const filtered = $scope.filteredWgMembers();
				const size = Number($scope.wgMemberPageSize) || 10;
				const start = ($scope.wgMemberCurrentPage - 1) * size;
				return filtered.slice(start, start + size);
			};

			$scope.changeWgMemberPage = function (delta) {
				const next = $scope.wgMemberCurrentPage + delta;
				if (next >= 1 && next <= $scope.wgMemberTotalPages) {
					$scope.wgMemberCurrentPage = next;
				}
			};

			$scope.getWgMemberRecordRange = function () {
				const total = $scope.filteredWgMembers().length;
				if (total === 0) {
					return { start: 0, end: 0, total: 0 };
				}
				const size = Number($scope.wgMemberPageSize) || 10;
				const start = ($scope.wgMemberCurrentPage - 1) * size + 1;
				const end = Math.min($scope.wgMemberCurrentPage * size, total);
				return { start: start, end: end, total: total };
			};

			$scope.onWgMemberSearchChange = function () {
				$scope.wgMemberCurrentPage = 1;
				$scope.updateWgMembersPagination();
			};

			$scope.onWgMemberPageSizeChange = function () {
				$scope.wgMemberCurrentPage = 1;
				$scope.updateWgMembersPagination();
			};

			$scope.getWgOwnerRoleSearchKeys = function () {
				if (!$scope.wgOwnerRoleHeaders || !$scope.wgOwnerRoleHeaders.length) {
					return ['name', 'displayName', 'type', 'status'];
				}
				return $scope.wgOwnerRoleHeaders.map(function (h) {
					return h.key;
				});
			};

			$scope.filteredWgOwnerRoles = function () {
				if (!Array.isArray($scope.wgOwnerRoles)) {
					return [];
				}
				const keyword = ($scope.wgOwnerRoleSearch || '').toLowerCase().trim();
				if (!keyword) {
					return $scope.wgOwnerRoles;
				}
				const keys = $scope.getWgOwnerRoleSearchKeys();
				return $scope.wgOwnerRoles.filter(function (role) {
					return keys.some(function (key) {
						const val = role[key];
						return val != null && String(val).toLowerCase().indexOf(keyword) !== -1;
					});
				});
			};

			$scope.updateWgOwnerRolesPagination = function () {
				const totalItems = $scope.filteredWgOwnerRoles().length;
				const size = Number($scope.wgOwnerRolePageSize) || 10;
				$scope.wgOwnerRoleTotalPages = Math.max(1, Math.ceil(totalItems / size) || 1);
				if ($scope.wgOwnerRoleCurrentPage > $scope.wgOwnerRoleTotalPages) {
					$scope.wgOwnerRoleCurrentPage = $scope.wgOwnerRoleTotalPages;
				}
				if ($scope.wgOwnerRoleCurrentPage < 1) {
					$scope.wgOwnerRoleCurrentPage = 1;
				}
			};

			$scope.paginatedWgOwnerRoles = function () {
				const filtered = $scope.filteredWgOwnerRoles();
				const size = Number($scope.wgOwnerRolePageSize) || 10;
				const start = ($scope.wgOwnerRoleCurrentPage - 1) * size;
				return filtered.slice(start, start + size);
			};

			$scope.changeWgOwnerRolePage = function (delta) {
				const next = $scope.wgOwnerRoleCurrentPage + delta;
				if (next >= 1 && next <= $scope.wgOwnerRoleTotalPages) {
					$scope.wgOwnerRoleCurrentPage = next;
				}
			};

			$scope.getWgOwnerRoleRecordRange = function () {
				const total = $scope.filteredWgOwnerRoles().length;
				if (total === 0) {
					return { start: 0, end: 0, total: 0 };
				}
				const size = Number($scope.wgOwnerRolePageSize) || 10;
				const start = ($scope.wgOwnerRoleCurrentPage - 1) * size + 1;
				const end = Math.min($scope.wgOwnerRoleCurrentPage * size, total);
				return { start: start, end: end, total: total };
			};

			$scope.onWgOwnerRoleSearchChange = function () {
				$scope.wgOwnerRoleCurrentPage = 1;
				$scope.updateWgOwnerRolesPagination();
			};

			$scope.onWgOwnerRolePageSizeChange = function () {
				$scope.wgOwnerRoleCurrentPage = 1;
				$scope.updateWgOwnerRolesPagination();
			};

			$scope.loadWgOwnerRoles = function () {
				if (!$scope.selectedWgId) {
					return;
				}
				$scope.wgOwnerRolesLoading = true;
				$http({
					method: 'GET',
					url: PluginHelper.getPluginRestUrl('rolemanagement/workgroup/' + encodeURIComponent($scope.selectedWgId) + '/roles'),
					headers: $scope.headerToken.headers
				}).then(
					function success(res) {
						const d = res.data || {};
						$scope.wgOwnerRoles = d.objects || [];
						$scope.wgOwnerRoleHeaders =
							d.headers && d.headers.length
								? d.headers
								: [
										{ key: 'name', label: 'Role Name' },
										{ key: 'displayName', label: 'Display Name' },
										{ key: 'type', label: 'Type' },
										{ key: 'status', label: 'Status' }
								  ];
						$scope.wgOwnerRolesLoading = false;
						$scope.updateWgOwnerRolesPagination();
					},
					function error() {
						$scope.wgOwnerRolesLoading = false;
						$scope.showToast('Could not load roles owned by workgroup', 'error');
					}
				);
			};

			$scope.setWgMembersTab = function (tab) {
				$scope.wgMembersActiveTab = tab;
				if (tab === 'roles' && $scope.wgOwnerRoleHeaders.length === 0 && !$scope.wgOwnerRolesLoading) {
					$scope.loadWgOwnerRoles();
				}
			};

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
				$scope.selectedWgId = wg.id;
				$scope.showWgMembersModal = true;
				$scope.wgMembersLoading = true;
				$scope.wgMembersActiveTab = 'members';
				$scope.wgMembers = [];
				$scope.wgMemberHeaders = [];
				$scope.wgMemberSearch = '';
				$scope.wgMemberPageSize = 10;
				$scope.wgMemberCurrentPage = 1;
				$scope.wgMemberTotalPages = 1;
				$scope.wgOwnerRoles = [];
				$scope.wgOwnerRoleHeaders = [];
				$scope.wgOwnerRolesLoading = false;
				$scope.wgOwnerRoleSearch = '';
				$scope.wgOwnerRolePageSize = 10;
				$scope.wgOwnerRoleCurrentPage = 1;
				$scope.wgOwnerRoleTotalPages = 1;
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
						$scope.updateWgMembersPagination();
					},
					function error() {
						$scope.wgMembersLoading = false;
						$scope.showToast('Could not load workgroup members', 'error');
					}
				);
			};

			$scope.closeWgMembersModal = function () {
				$scope.showWgMembersModal = false;
				$scope.wgMemberSearch = '';
				$scope.wgMemberCurrentPage = 1;
				$scope.wgOwnerRoleSearch = '';
				$scope.wgOwnerRoleCurrentPage = 1;
			};

			$scope.loadWgStats();
			$scope.loadWorkgroups();
		}
	];

	// ng-app is RoleApp — controller must be on RoleApp for $controller lookup.
	// WorkgroupApp is the home module for workgroup-specific services (add with WorkgroupApp.service, etc.).
	angular.module('RoleApp').controller('WorkgroupManagementController', workgroupManagementController);

	window.WorkgroupApp = WorkgroupApp;
})();
