/**
 * Workgroup Management — module WorkgroupApp (own services/controllers namespace).
 * ng-app is RoleApp, so the UI controller must also be registered on RoleApp
 * (submodule-only registration is not reliable for ng-app="RoleApp" in all IIQ builds).
 *
 * Load order: roleManagement.js (creates RoleApp) → this file → bulkRequest.js.
 */
(function () {
	'use strict';

	var WG_MODAL_SEARCH_MIN_LEN = 3;

	/** Match row values for configured table columns (name, NetworkId, displayName, status, etc.). */
	function wgRowMatchesColumns(row, keyword, headers) {
		var kw = (keyword || '').toLowerCase().trim();
		if (!kw || kw.length < WG_MODAL_SEARCH_MIN_LEN) {
			return true;
		}
		if (!row || typeof row !== 'object') {
			return false;
		}
		var keys = [];
		if (headers && headers.length) {
			for (var i = 0; i < headers.length; i++) {
				if (headers[i] && headers[i].key) {
					keys.push(headers[i].key);
				}
			}
		}
		if (keys.length === 0) {
			for (var key in row) {
				if (Object.prototype.hasOwnProperty.call(row, key)) {
					keys.push(key);
				}
			}
		}
		for (var j = 0; j < keys.length; j++) {
			var val = row[keys[j]];
			if (val != null && typeof val !== 'object' && String(val).toLowerCase().indexOf(kw) !== -1) {
				return true;
			}
		}
		return false;
	}

	var WorkgroupApp = angular.module('WorkgroupApp', []);

	var WG_TABLE_LOAD_MIN_MS = 1000;

	var workgroupManagementController = [
		'$scope',
		'$http',
		'$timeout',
		function ($scope, $http, $timeout) {
			var wgLoadSeq = 0;
			var wgLoadStartedAt = 0;
			var wgLoadEndTimer = null;
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
			$scope.wgSortColumn = 'name';
			$scope.wgSortDirection = 'ASC';
			$scope.workgroupSearchText = '';
			$scope.showWgMemberFilterDropdown = false;
			$scope.wgFilterMemberSearch = '';
			$scope.wgFilterMemberOptions = [];
			$scope.wgFilterMemberDraft = {};
			$scope.wgFilterMemberCache = {};
			$scope.wgFilterLoading = false;
			$scope.wgSelectedMemberFilters = [];
			$scope.showWgMoreFilterDropdown = false;
			$scope.wgMoreFilter = '';
			$scope.wgMoreFilterOptions = [
				{ value: '', label: 'All workgroups' },
				{ value: 'assignedToRoles', label: 'Show Workgroups assigned to roles' },
				{ value: 'singleMember', label: 'Show Single Member workgroups' },
				{ value: 'termedMember', label: 'Show Termed Member workgroups' }
			];
			$scope.showWgMembersModal = false;
			$scope.wgMembers = [];
			$scope.wgMemberHeaders = [];
			$scope.wgMembersTitle = '';
			$scope.wgMembersLoading = false;
			$scope.wgLoading = false;
			$scope.wgModalSearch = { member: '', role: '' };
			$scope.wgMemberPageSize = 10;
			$scope.wgMemberCurrentPage = 1;
			$scope.wgMemberTotalPages = 1;
			$scope.wgMembersActiveTab = 'members';
			$scope.selectedWgId = null;
			$scope.wgOwnerRoles = [];
			$scope.wgOwnerRoleHeaders = [];
			$scope.wgOwnerRolesLoading = false;
			$scope.wgOwnerRolePageSize = 10;
			$scope.wgOwnerRoleCurrentPage = 1;
			$scope.wgOwnerRoleTotalPages = 1;

			$scope.showWgCreateModal = false;
			$scope.wgCreateSaving = false;
			$scope.wgCreate = {
				name: '',
				description: '',
				email: '',
				ownerId: null,
				ownerLabel: '',
				notificationSetting: 'membersAndEmail'
			};
			$scope.wgNotificationOptions = [
				{ value: 'membersAndEmail', label: 'Notify members and group email' },
				{ value: 'membersOnly', label: 'Notify members only' },
				{ value: 'emailOnly', label: 'Notify group email only' },
				{ value: 'none', label: 'None' }
			];
			$scope.wgOwnerSearch = '';
			$scope.wgOwnerHits = [];
			$scope.wgMemberPickSearch = '';
			$scope.wgMemberHits = [];
			$scope.wgPendingMembers = [];

			$scope.wgStats = {};
			$scope.wgStatsLoading = true;

			/** % of all Bundle roles that list a workgroup as owner (Overview tile 2). */
			$scope.wgRolesOwnedPercent = function () {
				var tr = $scope.wgStats.totalRoles || 0;
				var ro = $scope.wgStats.rolesOwnedByWorkgroups || 0;
				if (tr === 0) {
					return 0;
				}
				return Math.round((ro / tr) * 1000) / 10;
			};

			$scope.wgSingleMemberPercent = function () {
				var t = $scope.wgStats.totalWorkgroups || 0;
				var s = $scope.wgStats.singleMemberWorkgroups || 0;
				if (t === 0) {
					return 0;
				}
				return Math.round((s / t) * 1000) / 10;
			};

			$scope.wgTermedPercent = function () {
				var t = $scope.wgStats.totalWorkgroups || 0;
				var w = $scope.wgStats.workgroupsWithTermedMembers || 0;
				if (t === 0) {
					return 0;
				}
				return Math.round((w / t) * 1000) / 10;
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
							totalRoles: 0,
							rolesOwnedByWorkgroups: 0,
							singleMemberWorkgroups: 0,
							workgroupsWithTermedMembers: 0
						};
						$scope.showToast('Could not load workgroup statistics', 'error');
					}
				);
			};

			$scope.filteredWgMembers = function () {
				if (!Array.isArray($scope.wgMembers)) {
					return [];
				}
				var keyword = ($scope.wgModalSearch.member || '').toLowerCase().trim();
				if (!keyword || keyword.length < WG_MODAL_SEARCH_MIN_LEN) {
					return $scope.wgMembers;
				}
				return $scope.wgMembers.filter(function (member) {
					return wgRowMatchesColumns(member, keyword, $scope.wgMemberHeaders);
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

			$scope.onWgMemberPageSizeChange = function () {
				$scope.wgMemberCurrentPage = 1;
				$scope.updateWgMembersPagination();
			};

			$scope.filteredWgOwnerRoles = function () {
				if (!Array.isArray($scope.wgOwnerRoles)) {
					return [];
				}
				var keyword = ($scope.wgModalSearch.role || '').toLowerCase().trim();
				if (!keyword || keyword.length < WG_MODAL_SEARCH_MIN_LEN) {
					return $scope.wgOwnerRoles;
				}
				return $scope.wgOwnerRoles.filter(function (role) {
					return wgRowMatchesColumns(role, keyword, $scope.wgOwnerRoleHeaders);
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

			$scope.onWgOwnerRolePageSizeChange = function () {
				$scope.wgOwnerRoleCurrentPage = 1;
				$scope.updateWgOwnerRolesPagination();
			};

			$scope.clearWgMemberSearch = function ($event) {
				if ($event) {
					$event.stopPropagation();
					$event.preventDefault();
				}
				$scope.wgModalSearch.member = '';
				$scope.wgMemberCurrentPage = 1;
				$scope.updateWgMembersPagination();
			};

			$scope.clearWgOwnerRoleSearch = function ($event) {
				if ($event) {
					$event.stopPropagation();
					$event.preventDefault();
				}
				$scope.wgModalSearch.role = '';
				$scope.wgOwnerRoleCurrentPage = 1;
				$scope.updateWgOwnerRolesPagination();
			};

			$scope.$watch('wgModalSearch.member', function () {
				$scope.wgMemberCurrentPage = 1;
				$scope.updateWgMembersPagination();
			});

			$scope.$watch('wgModalSearch.role', function () {
				$scope.wgOwnerRoleCurrentPage = 1;
				$scope.updateWgOwnerRolesPagination();
			});

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

			$scope.getWorkgroupPageLimit = function () {
				var size = parseInt($scope.workgroupPageSize, 10);
				if (isNaN(size) || size < 1) {
					return 25;
				}
				return size;
			};

			$scope.formatWgDate = function (timestamp) {
				$scope.debugLog("Enter formatDate");
				if (!timestamp) return '';
				const date = new Date(timestamp);
				return date.toLocaleString(undefined, {
					year: '2-digit',
					month: 'numeric',
					day: 'numeric',
					hour: 'numeric',
					minute: '2-digit',
					hour12: true
				});
				
			};

			$scope.wgSortBy = function (columnName) {
				if ($scope.wgSortColumn === columnName) {
					$scope.wgSortDirection = $scope.wgSortDirection === 'ASC' ? 'DESC' : 'ASC';
				} else {
					$scope.wgSortColumn = columnName;
					$scope.wgSortDirection = 'ASC';
				}
				$scope.workgroupCurrentPage = 1;
				$scope.loadWorkgroups();
			};

			$scope.finishWgTableLoading = function () {
				var elapsed = Date.now() - wgLoadStartedAt;
				var remain = Math.max(0, WG_TABLE_LOAD_MIN_MS - elapsed);
				if (wgLoadEndTimer) {
					$timeout.cancel(wgLoadEndTimer);
				}
				wgLoadEndTimer = $timeout(function () {
					$scope.wgLoading = false;
					wgLoadEndTimer = null;
				}, remain);
			};

			$scope.loadWorkgroups = function () {
				var pageSize = $scope.getWorkgroupPageLimit();
				var start = ($scope.workgroupCurrentPage - 1) * pageSize;
				const params = {
					start: start,
					limit: pageSize,
					sort: $scope.wgSortColumn || 'name',
					dir: $scope.wgSortDirection || 'ASC'
				};
				const q = ($scope.workgroupSearchText || '').trim();
				if (q.length > 0) {
					params.query = q;
				}
				if ($scope.wgSelectedMemberFilters.length > 0) {
					params.memberIds = $scope.wgSelectedMemberFilters
						.map(function (m) { return m.id; })
						.join(',');
				}
				if ($scope.wgMoreFilter) {
					params.moreFilter = $scope.wgMoreFilter;
				}
				var seq = ++wgLoadSeq;
				wgLoadStartedAt = Date.now();
				$scope.wgLoading = true;
				$http({
					method: 'GET',
					url: PluginHelper.getPluginRestUrl('rolemanagement/workgroups'),
					params: params,
					headers: $scope.headerToken.headers
				}).then(
					function success(res) {
						if (seq !== wgLoadSeq) {
							return;
						}
						const data = res.data || {};
						$scope.workgroupRows = data.objects || [];
						$scope.workgroupTotal = data.total != null ? data.total : 0;
						$scope.finishWgTableLoading();
					},
					function error() {
						if (seq !== wgLoadSeq) {
							return;
						}
						$scope.workgroupRows = [];
						$scope.workgroupTotal = 0;
						$scope.showToast('Failed to load workgroups', 'error');
						$scope.finishWgTableLoading();
					}
				);
			};

			$scope.triggerWorkgroupSearch = function () {
				$scope.workgroupCurrentPage = 1;
				$scope.loadWorkgroups();
			};
			
			$scope.clearWorkgroupSearch = function () {
				$scope.workgroupSearchText = '';
				$scope.workgroupCurrentPage = 1;
				$scope.loadWorkgroups();
			};

			$scope.toggleWgMemberFilterDropdown = function () {
				if ($scope.showWgMemberFilterDropdown) {
					$scope.showWgMemberFilterDropdown = false;
					return;
				}
				$scope.showWgMoreFilterDropdown = false;
				$scope.showWgMemberFilterDropdown = true;
				$scope.wgFilterMemberDraft = {};
				$scope.wgSelectedMemberFilters.forEach(function (m) {
					if (m && m.id) {
						$scope.wgFilterMemberDraft[m.id] = true;
					}
				});
				$scope.loadWgFilterMembers('');
			};

			$scope.loadWgFilterMembers = function (query) {
				const q = (query || '').trim();
				$scope.wgFilterLoading = true;
				$http({
					method: 'GET',
					url: PluginHelper.getPluginRestUrl('rolemanagement/identities/suggest'),
					params: { q: q, limit: 80 },
					headers: $scope.headerToken.headers
				}).then(
					function success(res) {
						const rows = res.data || [];
						$scope.wgFilterMemberOptions = rows.filter(function (r) {
							return r && r.id;
						});
						$scope.wgFilterMemberOptions.forEach(function (r) {
							$scope.wgFilterMemberCache[r.id] = r;
						});
						$scope.wgFilterLoading = false;
					},
					function error() {
						$scope.wgFilterMemberOptions = [];
						$scope.wgFilterLoading = false;
					}
				);
			};

			$scope.wgSuggestFilterMembers = function (query) {
				const q = (query != null ? String(query) : String($scope.wgFilterMemberSearch || '')).trim();
				$scope.loadWgFilterMembers(q);
			};

			$scope.toggleWgFilterMember = function (member) {
				if (!member || !member.id) {
					return;
				}
				const current = !!$scope.wgFilterMemberDraft[member.id];
				$scope.wgFilterMemberDraft[member.id] = !current;
			};

			$scope.toggleWgFilterAllVisible = function () {
				const allSelected = $scope.areAllWgFilterVisibleSelected();
				$scope.wgFilterMemberOptions.forEach(function (m) {
					if (m && m.id) {
						$scope.wgFilterMemberDraft[m.id] = !allSelected;
					}
				});
			};

			$scope.areAllWgFilterVisibleSelected = function () {
				if (!$scope.wgFilterMemberOptions.length) {
					return false;
				}
				return $scope.wgFilterMemberOptions.every(function (m) {
					return !!$scope.wgFilterMemberDraft[m.id];
				});
			};

			$scope.removeWgFilterMember = function (id) {
				const idKey = id != null ? String(id) : '';
				if (idKey && $scope.wgFilterMemberDraft) {
					$scope.wgFilterMemberDraft[idKey] = false;
				}
				$scope.wgSelectedMemberFilters = $scope.wgSelectedMemberFilters.filter(function (m) {
					return String(m.id) !== idKey;
				});
				$scope.triggerWorkgroupSearch();
			};

			$scope.applyWgMemberFilter = function () {
				const selected = [];
				Object.keys($scope.wgFilterMemberDraft).forEach(function (id) {
					if ($scope.wgFilterMemberDraft[id]) {
						const hit = $scope.wgFilterMemberCache[id];
						selected.push({
							id: id,
							name: (hit && hit.name) || id,
							displayName: (hit && hit.displayName) || (hit && hit.name) || id
						});
					}
				});
				$scope.wgSelectedMemberFilters = selected;
				$scope.showWgMemberFilterDropdown = false;
				$scope.triggerWorkgroupSearch();
			};

			$scope.clearWgMemberFilter = function () {
				$scope.wgSelectedMemberFilters = [];
				$scope.wgFilterMemberSearch = '';
				$scope.wgFilterMemberDraft = {};
				$scope.wgFilterMemberOptions = [];
				$scope.showWgMemberFilterDropdown = false;
				$scope.triggerWorkgroupSearch();
			};

			$scope.toggleWgMoreFilterDropdown = function () {
				if ($scope.showWgMoreFilterDropdown) {
					$scope.showWgMoreFilterDropdown = false;
					return;
				}
				$scope.showWgMemberFilterDropdown = false;
				$scope.showWgMoreFilterDropdown = true;
			};

			$scope.selectWgMoreFilter = function (value) {
				$scope.wgMoreFilter = value || '';
				$scope.showWgMoreFilterDropdown = false;
				$scope.triggerWorkgroupSearch();
			};

			$scope.clearWgMoreFilter = function () {
				$scope.wgMoreFilter = '';
				$scope.showWgMoreFilterDropdown = false;
				$scope.triggerWorkgroupSearch();
			};

			$scope.clearAllWgFilters = function () {
				$scope.workgroupSearchText = '';
				$scope.wgSelectedMemberFilters = [];
				$scope.wgFilterMemberSearch = '';
				$scope.wgFilterMemberDraft = {};
				$scope.wgFilterMemberOptions = [];
				$scope.wgMoreFilter = '';
				$scope.showWgMemberFilterDropdown = false;
				$scope.showWgMoreFilterDropdown = false;
				$scope.triggerWorkgroupSearch();
			};

			$scope.onWorkgroupPageChange = function (page, pageSize) {
				$scope.workgroupCurrentPage = page;
				$scope.workgroupPageSize = pageSize;
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
				$scope.wgModalSearch.member = '';
				$scope.wgModalSearch.role = '';
				$scope.wgMemberPageSize = 10;
				$scope.wgMemberCurrentPage = 1;
				$scope.wgMemberTotalPages = 1;
				$scope.wgOwnerRoles = [];
				$scope.wgOwnerRoleHeaders = [];
				$scope.wgOwnerRolesLoading = false;
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
										{ key: 'employeeid', label: 'NetworkId' },
										{ key: 'displayName', label: 'Display Name' },
										{ key: 'status', label: 'Status' }
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
				$scope.wgModalSearch.member = '';
				$scope.wgModalSearch.role = '';
				$scope.wgMemberCurrentPage = 1;
				$scope.wgOwnerRoleCurrentPage = 1;
			};

			$scope.resetWgCreateForm = function () {
				$scope.wgCreate = {
					name: '',
					description: '',
					email: '',
					ownerId: null,
					ownerLabel: '',
					notificationSetting: 'membersAndEmail'
				};
				$scope.wgOwnerSearch = '';
				$scope.wgOwnerHits = [];
				$scope.wgMemberPickSearch = '';
				$scope.wgMemberHits = [];
				$scope.wgPendingMembers = [];
				$scope.wgCreateSaving = false;
			};

			$scope.openWgCreateModal = function () {
				$scope.resetWgCreateForm();
				$scope.showWgCreateModal = true;
			};

			$scope.closeWgCreateModal = function () {
				if ($scope.wgCreateSaving) {
					return;
				}
				$scope.showWgCreateModal = false;
				$scope.resetWgCreateForm();
			};

			$scope.wgSuggestOwner = function () {
				const q = ($scope.wgOwnerSearch || '').trim();
				if (q.length < 2) {
					$scope.wgOwnerHits = [];
					return;
				}
				$http({
					method: 'GET',
					url: PluginHelper.getPluginRestUrl('rolemanagement/identities/suggest'),
					params: { q: q, limit: 15 },
					headers: $scope.headerToken.headers
				}).then(
					function success(res) {
						$scope.wgOwnerHits = res.data || [];
					},
					function error() {
						$scope.wgOwnerHits = [];
					}
				);
			};

			$scope.selectWgOwner = function (hit) {
				if (!hit || !hit.id) {
					return;
				}
				$scope.wgCreate.ownerId = hit.id;
				$scope.wgCreate.ownerLabel = (hit.displayName || hit.name || '') + ' (' + hit.name + ')';
				$scope.wgOwnerSearch = '';
				$scope.wgOwnerHits = [];
			};

			$scope.wgSuggestMembers = function () {
				const q = ($scope.wgMemberPickSearch || '').trim();
				if (q.length < 2) {
					$scope.wgMemberHits = [];
					return;
				}
				$http({
					method: 'GET',
					url: PluginHelper.getPluginRestUrl('rolemanagement/identities/suggest'),
					params: { q: q, limit: 20 },
					headers: $scope.headerToken.headers
				}).then(
					function success(res) {
						const rows = res.data || [];
						const pendingIds = {};
						$scope.wgPendingMembers.forEach(function (p) {
							pendingIds[p.id] = true;
						});
						$scope.wgMemberHits = rows.filter(function (r) {
							return r && r.id && !pendingIds[r.id];
						});
					},
					function error() {
						$scope.wgMemberHits = [];
					}
				);
			};

			$scope.addWgPendingMember = function (m) {
				if (!m || !m.id) {
					return;
				}
				const exists = $scope.wgPendingMembers.some(function (p) {
					return p.id === m.id;
				});
				if (exists) {
					return;
				}
				$scope.wgPendingMembers.push({
					id: m.id,
					name: m.name,
					displayName: m.displayName,
					firstname: m.firstname,
					lastname: m.lastname
				});
				$scope.wgMemberPickSearch = '';
				$scope.wgMemberHits = [];
			};

			$scope.removeWgPendingMember = function (id) {
				$scope.wgPendingMembers = $scope.wgPendingMembers.filter(function (p) {
					return p.id !== id;
				});
			};

			$scope.submitCreateWorkgroup = function () {
				const name = ($scope.wgCreate.name || '').trim();
				if (!name) {
					$scope.showToast('Name is required', 'warning');
					return;
				}
				$scope.wgCreateSaving = true;
				const payload = {
					name: name,
					description: ($scope.wgCreate.description || '').trim(),
					email: ($scope.wgCreate.email || '').trim(),
					notificationSetting: $scope.wgCreate.notificationSetting || 'membersAndEmail',
					memberIds: $scope.wgPendingMembers.map(function (p) {
						return p.id;
					})
				};
				if ($scope.wgCreate.ownerId) {
					payload.ownerId = $scope.wgCreate.ownerId;
				}
				$http({
					method: 'POST',
					url: PluginHelper.getPluginRestUrl('rolemanagement/workgroups'),
					data: payload,
					headers: $scope.headerToken.headers
				}).then(
					function success(res) {
						const d = res.data || {};
						$scope.wgCreateSaving = false;
						if (d.message) {
							$scope.showToast(d.message, 'warning');
						} else {
							$scope.showToast('Workgroup created', 'success');
						}
						$scope.showWgCreateModal = false;
						$scope.resetWgCreateForm();
						$scope.workgroupCurrentPage = 1;
						$scope.loadWorkgroups();
						$scope.loadWgStats();
					},
					function error(res) {
						$scope.wgCreateSaving = false;
						const d = res && res.data;
						let msg = 'Could not create workgroup';
						if (d && typeof d === 'object' && d.message) {
							msg = d.message;
						} else if (typeof d === 'string' && d.length > 0) {
							msg = d;
						}
						$scope.showToast(msg, 'error');
					}
				);
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
