var RoleApp = angular.module('RoleApp', ['ui.bootstrap', 'sailpoint.comment', 'sailpoint.util', 'sailpoint.config', 'sailpoint.identity.account']);
window.RoleApp = RoleApp;

RoleApp.config(['$httpProvider', function ($httpProvider) {
		$httpProvider.defaults.xsrfCookieName = "CSRF-TOKEN";
	}])
	/*.directive('ngBlurClose', function ($document) {
		return {
			restrict: 'A',
			scope: {
				ngBlurClose: '&'
			},
			link: function (scope, element) {
				function handleClick(event) {
					if (!element[0].contains(event.target)) {
						scope.$apply(scope.ngBlurClose);
					}
				}

				$document.on('click', handleClick);
				element.on('$destroy', function () {
					$document.off('click', handleClick);
				});
			}
		};
	})*/
	.controller('RoleController', ['$scope', '$http','$timeout', '$q', function ($scope, $http, $timeout, $q) {
		let roleOwnerSuggestTimer = null;
		// Fetch plugin config immediately after controller initializes
		
		const csrfToken = PluginHelper.getCsrfToken();
		if (csrfToken) {
			$http.defaults.headers.common['X-XSRF-TOKEN'] = csrfToken;
		}
		$scope.headerToken = {
			headers : {
				'X-XSRF-TOKEN' : 'sdasd12312312asdas'
			}
		};

		$timeout(function () {
			if (typeof $scope.initPlugin === 'function') {
				$scope.initPlugin();
			}
		}, 0);
		$scope.isLoading = true;
		$scope.rolesTableLoading = false;
		$scope.config = {};
		$scope.isDebugEnabled = false;
		$scope.initPlugin = function () {
			console.log('calling initPlugin');
			$http.get(PluginHelper.getPluginRestUrl("rolemanagement/config"), $scope.headerToken)
		        .then(function success(response) {
		            // Assume backend returns: { "enabled": true } or { "enabled": false }
		            if (response.data) {
						$scope.config = response.data;
		                $scope.isDebugEnabled = response.data.debugEnabled;
		                $scope.helpUrl = response.data.helpUrl;
						$scope.isRoleAdmin = response.data.isRoleAdmin;
		            } else {
		                $scope.isDebugEnabled = false;
		            }
		        }, function error(err) {
		            console.error("Failed to fetch debug flag:", err);
		            $scope.isDebugEnabled = false; // fallback
		        });
		};
		
		$scope.navigateTo = function (section) {
		  switch (section) {
		    case 'create':
		      $scope.openCreateRoleModal(); // your modal method
		      break;
		    case 'bulkUpload':
		      $scope.openBulkUploadModal(); // your modal method
		      break;
		    case 'tasks':
		      window.open(PluginHelper.getPluginPageUrl("tasks.xhtml"), '_blank');
		      break;
		    case 'reports':
		      window.open(PluginHelper.getPluginPageUrl("reports.xhtml"), '_blank');
		      break;
		  }
		};
		
		$scope.activeSection = 'dashboard';

		$scope.setSection = function (section) {
			console.error('setting section ',section);
		  $scope.activeSection = section;
		};


		
		
		
		$scope.displayNamePattern = /^[a-zA-Z0-9 ,_-]*$/;
		
		// Debug log wrapper – supports unlimited params
		$scope.debugLog = function () {
		    if ($scope.isDebugEnabled) {
		        console.log.apply(console, arguments); // passes all arguments
		    }
		};
		
		// Debug log wrapper – supports unlimited params
		$scope.errorLog = function () {		    
			console.error.apply(console, arguments); // passes all arguments		    
		};

		$scope.toast = {
			visible: false,
			message: '',
			type: ''
		};

		$scope.showToast = function (message, type = 'success') {
			$scope.debugLog("Enter showToast");
			$scope.toast.message = message;
			$scope.toast.type = type;
			$scope.toast.visible = true;

			setTimeout(function () {
				$scope.$apply(function () {
					$scope.toast.visible = false;
				});
			}, 3000);
			$scope.debugLog("Exit showToast");
		};
		
		$scope.openHelpPage = function () {
		  window.open($scope.config.helpUrl, '_blank');
		};

		
		

		$scope.roles = [];
		$scope.selectedRoles = [];
		$scope.searchText = '';
		$scope.showSearchBy = false;
		$scope.searchActive = false;
		$scope.searchableColumns = {};
		$scope.searchColumn = 'name';
		$scope.Math = window.Math;

		$scope.showRoleModal = false;
		$scope.showEditRoleModal = false;



		$scope.formatDate = function (timestamp) {
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

		/**
		 * @property string: currently active page.
		 */
		$scope.activePage = 'home';

		$scope.visibleColumns = {};
		$scope.allColumns = [];
		$scope.columnSearchText = '';
		$scope.showColumnMenu = false;
		$scope.sortColumn = "modified"; // Default sort column
		$scope.sortDirection = "DESC";

		$scope.toggleColumnMenu = function () {
			$scope.debugLog("Enter toggleColumnMenu was ",$scope.showColumnMenu);
			$scope.showColumnMenu = !$scope.showColumnMenu;
			$scope.debugLog("Exit toggleColumnMenu now is ",$scope.showColumnMenu);
		};

		$scope.loadColumns = function () {
			$scope.debugLog("Enter loadColumns");
			$http.get(PluginHelper.getPluginRestUrl("rolemanagement/columns"), $scope.headerToken)
				.then(function (res) {
					$scope.allColumns = res.data || [];
					$scope.debugLog("Loaded columns: ", $scope.allColumns);
					res.data.forEach(col => {
						$scope.visibleColumns[col.name] = col.defaultVisible;
					});
				})
				.catch(function (err) {
					$scope.errorLog("loadColumns :Failed to load column config", err);
				});
			$scope.debugLog("Exit loadColumns");
		};

		$scope.getRoleValue = function (role, key) {
			// Handle nested values like sysDescriptions.en_US
			$scope.debugLog("Enter getRoleValue role ",role," key is ",key);
			const value = key.includes('.')
				? key.split('.').reduce((obj, k) => obj && obj[k], role)
				: role[key];

			// Format timestamps for 'created' and 'modified' keys
			if ((key === 'created' || key === 'modified') && value) {
				const date = new Date(value);
				return date.toLocaleString(undefined, {
					month: 'numeric',
					day: 'numeric',
					year: '2-digit',
					hour: 'numeric',
					minute: '2-digit',
					hour12: true
				});
			}

			if (key === 'memberCount' && (value === null || value === undefined)) {
				return 0;
			}

			//$scope.debugLog("Exit getRoleValue value  ",value);
			return value;
			
		};

		// Pagination
		$scope.pageSize = 10;
		$scope.currentPage = 1;
		$scope.totalCount = 0;

		$scope.startIndex = 0;
		$scope.endIndex = 0;

		$scope.searchInput = '';
		let cancelRoleSearch = null;

		$scope.roleSearchText = '';
		$scope.noRolesFound = '';

		$scope.roleTypeFilter = 'all';
		$scope.roleOwnerId = null;
		$scope.roleOwnerLabel = '';
		$scope.roleOwnerSearch = '';
		$scope.roleOwnerHits = [];
		$scope.roleRequestableFilter = 'all';
		$scope.roleExtendedAttrSearch = '';
		$scope.roleExtendedAttrDraft = '';
		$scope.showRoleMoreFilters = false;
		$scope.showRoleTypeDropdown = false;
		$scope.showRoleOwnerDropdown = false;
		$scope.showRoleRequestableDropdown = false;
		$scope.roleOwnerShowMinHint = true;
		$scope.roleOwnerShowNoResults = false;

		$scope.$watchGroup(['roleOwnerSearch', 'roleOwnerHits'], function () {
			const t = String($scope.roleOwnerSearch || '').trim();
			const hits = $scope.roleOwnerHits;
			const n = angular.isArray(hits) ? hits.length : 0;
			$scope.roleOwnerShowMinHint = t.length < 2;
			$scope.roleOwnerShowNoResults = t.length >= 2 && n === 0;
		});

		$scope.roleTypeButtonLabel = function () {
			if ($scope.roleTypeFilter === 'it') {
				return 'IT';
			}
			if ($scope.roleTypeFilter === 'business') {
				return 'Business';
			}
			return 'All';
		};

		$scope.roleRequestableButtonLabel = function () {
			if ($scope.roleRequestableFilter === 'true') {
				return 'True';
			}
			if ($scope.roleRequestableFilter === 'false') {
				return 'False';
			}
			return 'All';
		};

		$scope.roleOwnerButtonLabel = function () {
			if (!$scope.roleOwnerId) {
				return 'Any';
			}
			const s = ($scope.roleOwnerLabel || '').trim();
			if (s.length > 36) {
				return s.substring(0, 33) + '…';
			}
			return s;
		};

		$scope.toggleRoleTypeDropdown = function () {
			if ($scope.showRoleTypeDropdown) {
				$scope.showRoleTypeDropdown = false;
				return;
			}
			$scope.showRoleOwnerDropdown = false;
			$scope.showRoleRequestableDropdown = false;
			$scope.showRoleMoreFilters = false;
			$scope.showRoleTypeDropdown = true;
		};

		$scope.toggleRoleOwnerDropdown = function () {
			if ($scope.showRoleOwnerDropdown) {
				$scope.showRoleOwnerDropdown = false;
				return;
			}
			$scope.showRoleTypeDropdown = false;
			$scope.showRoleRequestableDropdown = false;
			$scope.showRoleMoreFilters = false;
			$scope.showRoleOwnerDropdown = true;
			// Run after digest so ng-if panel exists; debounced helper sends request when q >= 2 chars
			$timeout(function () {
				$scope.suggestRoleOwner($scope.roleOwnerSearch, true);
			}, 0);
		};

		$scope.toggleRoleRequestableDropdown = function () {
			if ($scope.showRoleRequestableDropdown) {
				$scope.showRoleRequestableDropdown = false;
				return;
			}
			$scope.showRoleTypeDropdown = false;
			$scope.showRoleOwnerDropdown = false;
			$scope.showRoleMoreFilters = false;
			$scope.showRoleRequestableDropdown = true;
		};

		$scope.selectRoleType = function (v) {
			$scope.roleTypeFilter = v;
			$scope.showRoleTypeDropdown = false;
			$scope.applyRoleFilters();
		};

		$scope.selectRoleRequestable = function (v) {
			$scope.roleRequestableFilter = v;
			$scope.showRoleRequestableDropdown = false;
			$scope.applyRoleFilters();
		};

		$scope.buildRolesRequestParams = function (extra) {
			const params = {
				start: $scope.startIndex || 0,
				limit: $scope.pageSize || 10
			};
			if ($scope.sortColumn && $scope.sortColumn !== 'complaineScore') {
				params.sort = $scope.sortColumn;
				params.dir = $scope.sortDirection;
			}
			const q = ($scope.roleSearchText || '').trim();
			if (q.length >= 3) {
				params.query = q;
			}
			if ($scope.roleTypeFilter && $scope.roleTypeFilter !== 'all') {
				params.roleType = $scope.roleTypeFilter;
			}
			if ($scope.roleOwnerId) {
				params.ownerId = $scope.roleOwnerId;
			}
			if ($scope.roleRequestableFilter === 'true' || $scope.roleRequestableFilter === 'false') {
				params.requestable = $scope.roleRequestableFilter;
			}
			const ext = ($scope.roleExtendedAttrSearch || '').trim();
			if (ext.length >= 2) {
				params.extendedAttrQuery = ext;
			}
			if (extra && typeof extra === 'object') {
				Object.keys(extra).forEach(function (k) {
					params[k] = extra[k];
				});
			}
			return params;
		};

		$scope.loadData = function () {
			$scope.rolesTableLoading = true;
			$scope.isLoading = true;
			$scope.debugLog("Enter loadData ");
			if ($scope.allColumns.length === 0) {
				$scope.loadColumns();
			}
			$scope.updatePageBounds();
			$scope.debugLog('Loading roles with pagination: currentPage', $scope.currentPage, 'pageSize', $scope.pageSize);

			const q = ($scope.roleSearchText || '').trim();
			if (cancelRoleSearch) {
				cancelRoleSearch.resolve();
			}
			let timeoutPromise = null;
			if (q.length >= 3) {
				cancelRoleSearch = $q.defer();
				timeoutPromise = cancelRoleSearch.promise;
			}

			const req = {
				method: 'GET',
				url: PluginHelper.getPluginRestUrl("rolemanagement/roles"),
				params: $scope.buildRolesRequestParams(),
				headers: $scope.headerToken.headers
			};
			if (timeoutPromise) {
				req.timeout = timeoutPromise;
			}

			$http(req).then(function success(response) {
				const data = response.data;
				$scope.roles = data.objects || [];
				if ($scope.sortColumn === 'complaineScore') {
					$scope.sortComplianceScores();
				}
				$scope.totalCount = data.total || 0;
				$scope.updatePageBounds();
				if ($scope.totalCount === 0) {
					$scope.noRolesFound = q.length >= 3
						? 'No roles found with the given search'
						: "You don't own any roles";
				}
				$scope.rolesTableLoading = false;
				$scope.isLoading = false;
			}, function error() {
				$scope.roles = [];
				$scope.totalCount = 0;
				$scope.rolesTableLoading = false;
				$scope.isLoading = false;
			});
		};

		$scope.handleRoleSearchKey = function ($event) {
			$scope.debugLog("Enter handleRoleSearchKey ", $event, " and search text ", $scope.roleSearchText);
			if (($event.key === 13 || $event.key === 'Enter') && $scope.roleSearchText.length >= 3) {
				$scope.currentPage = 1;
				$scope.loadData();
			}
		};

		$scope.clearSearch = function () {
			$scope.roleSearchText = '';
			$scope.currentPage = 1;
			$scope.loadData();
		};

		$scope.triggerRoleSearch = function () {
			const t = ($scope.roleSearchText || '').trim();
			if (t.length > 0 && t.length < 3) {
				return;
			}
			$scope.currentPage = 1;
			$scope.loadData();
		};

		$scope.applyRoleFilters = function () {
			$scope.currentPage = 1;
			$scope.loadData();
		};

		$scope.clearRoleFilters = function () {
			if (roleOwnerSuggestTimer) {
				$timeout.cancel(roleOwnerSuggestTimer);
				roleOwnerSuggestTimer = null;
			}
			$scope.roleTypeFilter = 'all';
			$scope.roleOwnerId = null;
			$scope.roleOwnerLabel = '';
			$scope.roleOwnerSearch = '';
			$scope.roleOwnerHits = [];
			$scope.roleRequestableFilter = 'all';
			$scope.roleExtendedAttrSearch = '';
			$scope.roleExtendedAttrDraft = '';
			$scope.showRoleMoreFilters = false;
			$scope.showRoleTypeDropdown = false;
			$scope.showRoleOwnerDropdown = false;
			$scope.showRoleRequestableDropdown = false;
			$scope.currentPage = 1;
			$scope.loadData();
		};

		$scope.toggleRoleMoreFilters = function () {
			if ($scope.showRoleMoreFilters) {
				$scope.showRoleMoreFilters = false;
				return;
			}
			$scope.showRoleTypeDropdown = false;
			$scope.showRoleOwnerDropdown = false;
			$scope.showRoleRequestableDropdown = false;
			$scope.showRoleMoreFilters = true;
			$scope.roleExtendedAttrDraft = $scope.roleExtendedAttrSearch || '';
		};

		$scope.applyRoleMoreFilters = function () {
			$scope.roleExtendedAttrSearch = ($scope.roleExtendedAttrDraft || '').trim();
			$scope.showRoleMoreFilters = false;
			$scope.applyRoleFilters();
		};

		$scope.clearRoleMoreFilters = function () {
			$scope.roleExtendedAttrDraft = '';
			$scope.roleExtendedAttrSearch = '';
			$scope.showRoleMoreFilters = false;
			$scope.applyRoleFilters();
		};

		$scope.suggestRoleOwner = function (typed, skipDebounce) {
			if (roleOwnerSuggestTimer) {
				$timeout.cancel(roleOwnerSuggestTimer);
				roleOwnerSuggestTimer = null;
			}
			const run = function () {
				const q = typed !== undefined && typed !== null
					? String(typed).trim()
					: String($scope.roleOwnerSearch || '').trim();
				if (q.length < 2) {
					$scope.roleOwnerHits = [];
					return;
				}
				$http({
					method: 'GET',
					url: PluginHelper.getPluginRestUrl('rolemanagement/identities/suggest'),
					params: { q: q, limit: 15, includeWorkgroups: true },
					headers: $scope.headerToken.headers
				}).then(
					function success(res) {
						let rows = res.data;
						if (!angular.isArray(rows)) {
							rows = [];
						}
						$scope.roleOwnerHits = rows;
					},
					function error() {
						$scope.roleOwnerHits = [];
					}
				);
			};
			const delay = skipDebounce ? 0 : 250;
			roleOwnerSuggestTimer = $timeout(function () {
				roleOwnerSuggestTimer = null;
				run();
			}, delay);
		};

		$scope.selectRoleOwner = function (hit) {
			if (!hit || !hit.id) {
				return;
			}
			$scope.roleOwnerId = hit.id;
			var kind = hit.workgroup ? 'Workgroup' : 'Identity';
			$scope.roleOwnerLabel = (hit.displayName || hit.name || '');
			$scope.roleOwnerSearch = '';
			$scope.roleOwnerHits = [];
			$scope.showRoleOwnerDropdown = false;
			$scope.applyRoleFilters();
		};

		$scope.clearRoleOwner = function () {
			if (roleOwnerSuggestTimer) {
				$timeout.cancel(roleOwnerSuggestTimer);
				roleOwnerSuggestTimer = null;
			}
			$scope.roleOwnerId = null;
			$scope.roleOwnerLabel = '';
			$scope.roleOwnerSearch = '';
			$scope.roleOwnerHits = [];
			$scope.showRoleOwnerDropdown = false;
			$scope.applyRoleFilters();
		};
		
		/** Icons / foot text for role dashboard tiles (match Workgroup overview styling). */
		$scope.dashboardCardIconStyle = function (card) {
			const t = (card && card.title ? String(card.title) : '').trim();
			if (t === 'Total Roles') {
				return { wrap: 'rm-metric-icon--cyan', icon: 'fa-layer-group' };
			}
			if (t === 'Enabled/Requestable') {
				return { wrap: 'rm-metric-icon--indigo', icon: 'fa-circle-check' };
			}
			if (t === 'Enabled/Non-Requestable') {
				return { wrap: 'rm-metric-icon--slate', icon: 'fa-lock' };
			}
			if (t === 'Disabled') {
				return { wrap: 'rm-metric-icon--rose', icon: 'fa-ban' };
			}
			return { wrap: 'rm-metric-icon--soft', icon: 'fa-chart-pie' };
		};

		$scope.dashboardCardFoot = function (card) {
			const t = (card && card.title ? String(card.title) : '').trim();
			if (t === 'Total Roles') {
				return 'IT & Business roles';
			}
			if (t === 'Enabled/Requestable') {
				return 'Active & requestable';
			}
			if (t === 'Enabled/Non-Requestable') {
				return 'Active, not requestable';
			}
			if (t === 'Disabled') {
				return 'Inactive roles';
			}
			return '';
		};

		$scope.filterByCard = function (card) {
			const params = $scope.buildRolesRequestParams();
			if (card && card.filter) {
				params.filter = card.filter;
			}
		  $http({
				method: 'GET',
				url: PluginHelper.getPluginRestUrl("rolemanagement/roles"),
				params: params,
				headers: $scope.headerToken.headers
			}).then(function success(response) {
				const data = response.data;
				$scope.roles = data.objects || [];
				$scope.totalCount = data.total || 0;
				if ($scope.totalCount === 0){
					$scope.noRolesFound = "No Roles found";
				}
				$scope.currentPageInput = 1;
				$scope.updatePageBounds();
			}, function error() {
				$scope.roles = [];
				$scope.totalCount = 0;
			});
		}

		
		$scope.getComplianceColor = function (score) {
		  if (score === 100) {
		    return '#2ecc71'; // Green
		  } else if (score >= 70 && score < 100) {
		    return '#f1c40f'; // Yellow
		  } else {
		    return '#e74c3c'; // Red
		  }
		};

		$scope.getComplianceSortValue = function (role) {
			if (!role) return -1;
			var value = role.complianceScore;
			if (value === undefined || value === null) {
				value = role.complaineScore;
			}
			var parsed = Number(value);
			return isNaN(parsed) ? -1 : parsed;
		};

		$scope.sortComplianceScores = function () {
			var direction = $scope.sortDirection === 'ASC' ? 1 : -1;
			$scope.roles.sort(function (a, b) {
				var aValue = $scope.getComplianceSortValue(a);
				var bValue = $scope.getComplianceSortValue(b);
				if (aValue === bValue) return 0;
				return aValue > bValue ? direction : -direction;
			});
		};


		$scope.sortBy = function (columnName) {
			if (columnName === 'complaineScore') {
				if ($scope.sortColumn === columnName) {
					$scope.sortDirection = $scope.sortDirection === 'ASC' ? 'DESC' : 'ASC';
				} else {
					$scope.sortColumn = columnName;
					$scope.sortDirection = 'ASC';
				}
				$scope.sortComplianceScores();
				return;
			}
			if ($scope.sortColumn === columnName) {
				$scope.sortDirection = $scope.sortDirection === 'ASC' ? 'DESC' : 'ASC';
			} else {
				$scope.sortColumn = columnName;
				$scope.sortDirection = 'ASC';
			}
			$scope.loadData();
		};



		$scope.updatePageBounds = function () {
			$scope.startIndex = ($scope.currentPage - 1) * $scope.pageSize;
			$scope.endIndex = Math.min($scope.startIndex + $scope.pageSize, $scope.totalCount);
		};


		$scope.onRolePageChange = function (page, pageSize) {
			$scope.currentPage = page;
			$scope.pageSize = pageSize;
			$scope.selectedRoles = [];
			$scope.roles.forEach(function (role) { role.selected = false; });
			$scope.loadData();
		};

		$scope.changePageSize = function () {
			$scope.currentPage = 1;
			$scope.selectedRoles = []
			$scope.roles.forEach(role => role.selected = false);
			$scope.loadData();
		};

		$scope.goToFirstPage = function () {
			$scope.currentPage = 1;
			$scope.loadData();
		};

		$scope.goToPreviousPage = function () {
			if ($scope.currentPage > 1) {
				$scope.currentPage--;
				$scope.loadData();
			}
		};

		$scope.goToNextPage = function () {
			if (($scope.currentPage * $scope.pageSize) < $scope.totalCount) {
				$scope.currentPage++;
				$scope.loadData();
			}
		};

		$scope.goToLastPage = function () {
			$scope.currentPage = Math.ceil($scope.totalCount / $scope.pageSize);
			$scope.loadData();
		};

		$scope.checkEnterKey = function (event, callback) {
			if (event && event.which === 13) {  // 13 is the key code for Enter
				event.preventDefault();
				if (typeof callback === 'function') {
					callback();
				}
			}
		};


		$scope.currentPageInput = 1; // use this for input field

		$scope.validateAndGoToPage = function () {
			let page = parseInt($scope.currentPageInput);
			const maxPage = Math.ceil($scope.totalCount / $scope.pageSize);

			if (isNaN(page) || page < 1) {
				page = 1;
			} else if (page > maxPage) {
				page = maxPage;
			}

			$scope.currentPage = page;
			$scope.currentPageInput = page; // update input field with corrected value
			$scope.loadData();
		};



		$scope.toggleAll = function () {
		  const pagedRoles = $scope.roles.slice($scope.startIndex, $scope.endIndex);
		  const allSelected = pagedRoles.every(role => role.selected);
		
		  pagedRoles.forEach(role => role.selected = !allSelected);
		
		  $scope.updateSelection();
		};

		
		$scope.isAllSelectedOnCurrentPage = function () {
		  const pagedRoles = $scope.roles.slice($scope.startIndex, $scope.endIndex);
		  return pagedRoles.length > 0 && pagedRoles.every(role => role.selected);
		};



		$scope.updateSelection = function () {
			$scope.selectedRoles = $scope.roles.filter(role => role.selected);
		};

		$scope.toggleSearchBy = function () {
			$scope.showSearchBy = !$scope.showSearchBy;
		};
		$scope.applySearchFilter = function () {
			$scope.searchActive = true;
			$scope.showSearchBy = false;
		};

		$scope.columnMatch = function (role) {
			const search = $scope.searchText.toLowerCase();
			if (!search) return true;

			return Object.keys($scope.searchableColumns).some(col => {
				if ($scope.searchableColumns[col]) {
					const val = (role[col] || '').toString().toLowerCase();
					return val.includes(search);
				}
				return false;
			});
		};

		$scope.showActionMenu = false;

		$scope.toggleActionMenu = function () {
			$scope.showActionMenu = !$scope.showActionMenu;
		};

		$scope.actionPerforming = null;
		$scope.performAction = function (action) {
			//alert(`Performing action: ${action} on ${$scope.selectedRoles.length} role(s)`);
			console.log(`Action: ${action}, Selected Roles:`, $scope.selectedRoles);
			$scope.showActionMenu = false;
			$scope.showNotificationModal = true;
			$scope.actionPerforming = action;
			//$scope.openNotificationModal();
		};

		/* Bulk Actions */
		$scope.showNotificationModal = false;


		$scope.notificationSettings = {
			enabled: false,
			agree: false,
			attachFile: false,
			recipients: []
		};
		let identityMultiSuggest = null;


		$scope.addMultiSuggest = function () {
			
			console.log('addMultiSuggest CALLED');
			// Delay initialization to ensure DOM is rendered
			$timeout(function () {
				console.log('Initializing MultiSuggest for email recipients');
				const suggestDiv = document.getElementById('emailRecipientMultiSuggest');
				console.log('Suggest Div:', suggestDiv);
				if (suggestDiv && !suggestDiv.hasChildNodes()) {
					console.log('Creating MultiSuggest instance');
					identityMultiSuggest = new SailPoint.MultiSuggest({
						renderTo: 'emailRecipientMultiSuggest',
						suggestType: 'identity',
						jsonData: { "totalCount": 0, "objects": [] },
						border: true,
						baseParams: { context: 'Owner' },
						inputFieldName: 'identitySuggest',
						valueField: 'name',
						contextPath: CONTEXT_PATH
					});
				}
			}, 100); // 100ms is typically enough
		};

		$scope.closeNotificationModal = function () {
			$scope.showNotificationModal = false;
			$scope.notificationSettings = {
				enabled: false,
				agree: false,
				attachFile: false,
				recipients: []
			};
		};

		$scope.submitBulkAction = function () {
			console.log('Submitting bulk action:',$scope.notificationSettings);
			$scope.showNotificationModal = false;
			const payload = {
				action: $scope.actionPerforming,
				roles: $scope.selectedRoles.map(r => r.id),
				notification: {
					enabled: $scope.notificationSettings.enabled,
					attachFile: $scope.notificationSettings.attachFile,
					recipients: [] // This will be handled below
				}
			};

			if (!identityMultiSuggest) {
				console.warn('MultiSuggest instance not found');
			}
			else{
				console.warn('MultiSuggest instance found ',identityMultiSuggest);
				const selectedIdentities = identityMultiSuggest.getValue(); // returns array of identity names
  				console.log('Selected Identities:', selectedIdentities);
			}
			console.log('Final payload for bulk action:', payload);

			$http.post(PluginHelper.getPluginRestUrl("rolemanagement/roles/bulk-action"), payload)
				.then(function (response) {
					$scope.showToast("Bulk operation initiated successfully", "success");
				})
				.catch(function (error) {
					console.error("Error submitting bulk action", error);
					$scope.showToast("Failed to initiate bulk operation", "error");
				});
		};

		/* Bulk actions end */

		// Reusable function to fetch role details
		$scope.loadRoleDetails = function (roleId, onSuccess) {
			$http.get(PluginHelper.getPluginRestUrl("rolemanagement/role/" + encodeURIComponent(roleId)))
				.then(function (response) {
					$scope.selectedRole = response.data;
					console.log('role data ', $scope.selectedRole);
					$scope.collapsedRoles = {};
					$scope.collapseAllRoles();
					$scope.groupITRolesByEntitlement(); // Optional if needed for both view/edit	      
					if (onSuccess) onSuccess(response.data);
				})
				.catch(function (err) {
					console.error("Failed to load role details", err);
					alert("Error loading role details");
				});
		};

		// View role modal
		$scope.viewRole = function (role) {
			$scope.loadRoleDetails(role.id, function () {
				$scope.showRoleModal = true;
				$scope.setTab('details');
			});
		};

		// Edit role modal (new usage example)
		$scope.editRole = function (role) {
			console.log('Edit role called ');
			$scope.loadRoleDetails(role.id, function () {
				$scope.showEditRoleModal = true;
				$scope.setTab('edit-details');
			});
		};


		$scope.closeRoleModal = function () {
			$scope.showRoleModal = false;
		};

		$scope.setTab = function (tab) {
			$scope.activeTab = tab;
		};

		$scope.isTab = function (tab) {
			return $scope.activeTab === tab;
		};

		$scope.setPage = function (pageName) {
			$scope.activePage = pageName;
		}
		$scope.isSet = function (pageName) {
			console.log('checking ', pageName, 'is set ', $scope.activePage == pageName);
			return ($scope.activePage == pageName);
		}


		$scope.workgroupMembers = [];
		$scope.workgroupMemberHeaders = [];
		$scope.workgroupSearch = '';
		$scope.workgroupPageSize = 5;
		$scope.workgroupCurrentPage = 1;
		$scope.workgroupTotalPages = 1;

		$scope.loadGroupMembers = function ($event, ownerId) {
			console.log('calling loadGroupMembers total ');
			if ($event && $event.stopPropagation) $event.stopPropagation();

			// CLEAR DATA BEFORE API CALL
		    $scope.workgroupMembers = [];
		    $scope.workgroupMemberHeaders = [];
		    $scope.workgroupCurrentPage = 1;
		    $scope.workgroupTotalPages = 1;
		    $scope.workgroupSearch = '';
    
			$http({
				method: "GET",
				url: PluginHelper.getPluginRestUrl("rolemanagement/workgroup/" + ownerId)
			}).then(function success(res) {
				if (res.data.headers && res.data.objects) {
				  // new format with keys
				  $scope.workgroupMemberHeaders = res.data.headers;
				  $scope.workgroupMembers = res.data.objects;
				  console.log("workgroup members ",$scope.workgroupMembers);
				} else {
				  // fallback if only data is returned
				  $scope.workgroupMembers = res.data;
				  $scope.workgroupMemberHeaders = [
				    { label: "Name", key: "name" },
				    { label: "Employee Number", key: "empNumber" },
				    { label: "Display Name", key: "displayName" }
				  ];
				}
				$scope.workgroupMembersLoaded = true;
		        $scope.workgroupSearch = '';
		        $scope.workgroupCurrentPage = 1;
		        $scope.updateWorkgroupPagination();
			}, function error(err) {
				console.error("Failed to fetch group members", err);
				alert("Could not load group members.");
			});
		};

		$scope.switchToWorkgroupTab = function () {
			console.log('switchToWorkgroupTab: total ',$scope.workgroupMembers.length,' workgroupCurrentPage ',$scope.workgroupCurrentPage);
			$scope.setTab('workgroup');		
			$scope.loadGroupMembers(null, $scope.selectedRole.ownerId);
			
		};

		$scope.roleMembers = [];
		$scope.roleMemberHeaders = [];
		$scope.roleMembersTotal = 0;
		$scope.roleMembersLoaded = false;
		$scope.roleMembersLoading = false;
		$scope.roleMembersPreviewLimit = 50;
		$scope.roleMembersDownloadInProgress = false;

		$scope.loadRoleMembers = function (roleId) {
			if (!roleId) {
				return;
			}
			$scope.roleMembers = [];
			$scope.roleMemberHeaders = [];
			$scope.roleMembersTotal = 0;
			$scope.roleMembersLoaded = false;
			$scope.roleMembersLoading = true;

			$http.get(PluginHelper.getPluginRestUrl("rolemanagement/role/" + encodeURIComponent(roleId) + "/members"), {
				params: {
					start: 0,
					limit: $scope.roleMembersPreviewLimit
				}
			}).then(function (res) {
				var data = res.data || {};
				$scope.roleMemberHeaders = data.headers || [
					{ label: "Identity Name", key: "name" },
					{ label: "First Name", key: "firstName" },
					{ label: "Last Name", key: "lastName" },
					{ label: "Employee ID", key: "employeeId" }
				];
				$scope.roleMembers = data.objects || [];
				$scope.roleMembersTotal = data.total != null ? data.total : $scope.roleMembers.length;
			}, function (err) {
				console.error("Failed to fetch role members", err);
				$scope.showToast("Could not load role members.", "error");
			}).finally(function () {
				$scope.roleMembersLoading = false;
				$scope.roleMembersLoaded = true;
			});
		};

		$scope.switchToRoleMembersTab = function () {
			$scope.setTab('members');
			$scope.loadRoleMembers($scope.selectedRole.id);
		};

		$scope.downloadRoleMembers = function () {
			var roleId = $scope.selectedRole && $scope.selectedRole.id;
			if (!roleId) {
				return;
			}
			$scope.roleMembersDownloadInProgress = true;
			$http.get(PluginHelper.getPluginRestUrl("rolemanagement/role/" + encodeURIComponent(roleId) + "/members/download"), {
				responseType: "text"
			}).then(function (response) {
				var roleName = ($scope.selectedRole && $scope.selectedRole.name) ? $scope.selectedRole.name : "role";
				var blob = new Blob([response.data || ""], { type: "text/csv;charset=utf-8;" });
				var url = window.URL.createObjectURL(blob);
				var link = document.createElement("a");
				link.href = url;
				link.download = roleName + "-members.csv";
				document.body.appendChild(link);
				link.click();
				document.body.removeChild(link);
				window.URL.revokeObjectURL(url);
				$scope.showToast("Download started", "success");
			}).catch(function (error) {
				console.error("Failed to download role members", error);
				$scope.showToast("Download failed", "error");
			}).finally(function () {
				$scope.roleMembersDownloadInProgress = false;
			});
		};
		
		// Holds all keys to filter with, extracted from headers
		$scope.getWorkgroupSearchKeys = function () {
		  if (!$scope.workgroupMemberHeaders) return [];
		  return $scope.workgroupMemberHeaders.map(function (header) {
		    return header.key;
		  });
		};

		// Filters based on workgroupSearch value across dynamic keys
		$scope.filteredWorkgroupMembers = function () {
			console.log('filteredWorkgroupMembers: total ',$scope.workgroupMembers.length,' workgroupCurrentPage ',$scope.workgroupCurrentPage);
			console.log("before filteredWorkgroupMembers");
		  if (!Array.isArray($scope.workgroupMembers)) return [];
		
		  console.log("after filteredWorkgroupMembers");
		  const keyword = ($scope.workgroupSearch || '').toLowerCase();
		  const keys = $scope.getWorkgroupSearchKeys();
		
		  return $scope.workgroupMembers.filter(function (member) {
		    return keys.some(function (key) {
		      return (member[key] || '').toLowerCase().includes(keyword);
		    });
		  });
		};
		
		// Paginated data based on current page
		$scope.paginatedWorkgroupMembers = function () {
			console.log('paginatedWorkgroupMembers: total ',$scope.workgroupMembers.length,' workgroupCurrentPage ',$scope.workgroupCurrentPage);
			
		  const start = ($scope.workgroupCurrentPage - 1) * $scope.workgroupPageSize;
		  const end = start + $scope.workgroupPageSize;
		  console.log("filteredworkgroupsmembers "+$scope.filteredWorkgroupMembers());
		  return $scope.filteredWorkgroupMembers().slice(start, end);
		};
		
		// Recalculate total pages
		$scope.updateWorkgroupPagination = function () {
			console.log('updateWorkgroupPagination: total ',$scope.workgroupMembers.length,' workgroupCurrentPage ',$scope.workgroupCurrentPage);
			
		  const totalItems = $scope.filteredWorkgroupMembers().length;
		  $scope.workgroupTotalPages = Math.ceil(totalItems / $scope.workgroupPageSize) || 1;
		  
		};
		
		// Page change handler
		$scope.changeWorkgroupPage = function (delta) {
			console.log('changeWorkgroupPage: total ',$scope.workgroupMembers.length,' workgroupCurrentPage ',$scope.workgroupCurrentPage);
			
		  const newPage = $scope.workgroupCurrentPage + delta;
		  if (newPage >= 1 && newPage <= $scope.workgroupTotalPages) {
		    $scope.workgroupCurrentPage = newPage;
		  }
		};
		
		$scope.getWorkgroupRecordRange = function () {
		  const start = ($scope.workgroupCurrentPage - 1) * $scope.workgroupPageSize + 1;
		  const end = Math.min($scope.workgroupCurrentPage * $scope.workgroupPageSize, $scope.filteredWorkgroupMembers().length);
		  const total = $scope.filteredWorkgroupMembers().length;
		
		  return { start, end, total };
		};


		/* IT Roles Code */
		$scope.groupITRolesByEntitlement = function () {
			const groups = {};
			($scope.selectedRole.itRoles || []).forEach(function (role) {
				const entitlement = role.entitlements || 'Unknown';
				if (!groups[entitlement]) {
					groups[entitlement] = [];
				}
				groups[entitlement].push(role);
			});
			$scope.groupedITRoles = groups;
		};

		$scope.collapsedITRoles = {};

		$scope.toggleCollapse = function (entitlement) {
			$scope.collapsedITRoles[entitlement] = !$scope.collapsedITRoles[entitlement];
		};

		/* Role Update Changes */

		$scope.closeEditRoleModal = function () {
			$scope.showEditRoleModal = false;
		};
		
		$scope.switchTab = function(tabName) {
			if ($scope.editRoleForm.$invalid) {
		        // Mark all invalid fields as touched
		        angular.forEach($scope.editRoleForm.$error, function (fields) {
		            angular.forEach(fields, function (field) {
						console.log("field ",field);
		                field.$setTouched();
		            });
		        });
		        return; // prevent submit
		    }
		    else {
				$scope.setTab(tabName);
			}
		}

		// Save updated role
		$scope.saveEditedRole = function () {
			console.log('updating role ', $scope.selectedRole);
			if ($scope.editRoleForm.$invalid) {
		        // Mark all invalid fields as touched
		        angular.forEach($scope.editRoleForm.$error, function (fields) {
		            angular.forEach(fields, function (field) {
		                field.$setTouched();
		            });
		        });
		        return; // prevent submit
		    }

		    // If valid → proceed with save
		    console.log("Form valid, saving role...");
			$scope.showToast("Updating role!", "info");
			$http({
				method: 'POST',
				url: PluginHelper.getPluginRestUrl("rolemanagement/role/update"),
				data: $scope.selectedRole
			}).then(function success(response) {
				var data = response.data;
				var status = (data.status || "").toLowerCase();

		        if (status === "success") {
		            $scope.showToast("Role updated successfully!", "success");
		        } else if (status === "error") {
		            $scope.showToast(data.message || "Failed to update role", "error");
		        } else if (status === "warn") {
		            $scope.showToast(data.message || "Update completed with warnings", "warning");
		        } else {
		            $scope.showToast("Unexpected response from server", "info");
		        }
				$scope.showEditRoleModal = false;
				$scope.loadData();  // Refresh table
			}, function error(err) {
				$scope.showToast("Failed to update role", "error");
				//alert("Failed to update role");
			});
		};

		// Entitlement logic
		$scope.availableApplications = ["LDAP", "Finance", "SAP"]; // Example

		$scope.availableAttributes = {
			"LDAP": ["cn", "ou"],
			"Finance": ["division", "role"]
		};

		$scope.availableEntitlements = {
			"cn": ["Users", "Admins"],
			"ou": ["Operations", "HR"],
			"division": ["Payroll", "Accounts"],
			"role": ["Manager", "Viewer"]
		};

		$scope.newEntitlement = {};

		// Mark existing entitlement for deletion
		$scope.markEntitlementForDeletion = function (entitlement) {
			entitlement._deleted = true;
		};

		// Load attributes when application changes
		$scope.loadAttributesForApp = function (application) {
			$scope.newEntitlement.attribute = '';
			$scope.newEntitlement.value = '';
		};

		// Load entitlement values when attribute changes
		$scope.loadEntitlementsForAttribute = function (app, attribute) {
			$scope.newEntitlement.value = '';
		};

		// Add new entitlement to role
		$scope.addNewEntitlement = function () {
			if ($scope.newEntitlement.application && $scope.newEntitlement.attribute && $scope.newEntitlement.value) {
				$scope.selectedRole.entitlements.push({
					applicationName: $scope.newEntitlement.application,
					property: $scope.newEntitlement.attribute,
					displayValue: $scope.newEntitlement.value
				});
				$scope.newEntitlement = {};
			} else {
				alert("Please select Application, Attribute, and Entitlement.");
			}
		};

		// Collapse tracking object
		//$scope.collapsedRoles = {};

		$scope.expandAllRoles = function () {
			if ($scope.selectedRole && Array.isArray($scope.selectedRole.itRoles)) {
				$scope.selectedRole.itRoles.forEach(function (role) {
					if (role.name) $scope.collapsedRoles[role.name] = false;
				});
			}
		};

		$scope.collapseAllRoles = function () {
			if ($scope.selectedRole && Array.isArray($scope.selectedRole.itRoles)) {
				$scope.selectedRole.itRoles.forEach(function (role) {
					if (role.name) $scope.collapsedRoles[role.name] = true;
				});
			}
		};

		$scope.toggleITRoleCollapse = function (roleName) {
			if (!$scope.collapsedRoles) $scope.collapsedRoles = {};
			$scope.collapsedRoles[roleName] = !$scope.collapsedRoles[roleName];
		};

		/* IT Role Selection */
		$scope.itRoleSearchText = '';
		$scope.itRoleSearchResults = [];
		$scope.showITRoleDropdown = true;
		$scope.itRoleSearchTriggered = false; // NEW FLAG

		let itRoleSearchTimeout = null;

		$scope.onITRoleSearchChange = function () {
			$scope.itRoleSearchTriggered = false;

			if (itRoleSearchTimeout) clearTimeout(itRoleSearchTimeout);

			if ($scope.itRoleSearchText.length >= 3) {
				$scope.fetchITRoles($scope.itRoleSearchText);
			} else if ($scope.itRoleSearchText.length > 0) {
				itRoleSearchTimeout = setTimeout(function () {
					$scope.$apply(function () {
						$scope.fetchITRoles($scope.itRoleSearchText);
					});
				}, 2000);
			} else {
				$scope.itRoleSearchResults = [];
				$scope.showITRoleDropdown = false;
			}
		};

		$scope.fetchITRoles = function (query) {
			$http.get(PluginHelper.getPluginRestUrl("rolemanagement/itroles/search?q=" + encodeURIComponent(query)))
				.then(function (response) {
					$scope.itRoleSearchResults = response.data || [];
					$scope.showITRoleDropdown = true;
					$scope.itRoleSearchTriggered = true;
				})
				.catch(function (err) {
					console.error("Failed to search IT Roles", err);
					$scope.itRoleSearchResults = [];
					$scope.showITRoleDropdown = true;
					$scope.itRoleSearchTriggered = true;
				});
		};


		$scope.selectITRole = function (role) {
			$http.get(PluginHelper.getPluginRestUrl("rolemanagement/itroles/details/" + encodeURIComponent(role.id)))
				.then(function (response) {
					const newITRole = response.data;
					newITRole.isNew = true;  // mark it as newly added
					if (!$scope.selectedRole.itRoles) {
						$scope.selectedRole.itRoles = [];
					}
					$scope.selectedRole.itRoles.push(newITRole);
				})
				.catch(function (err) {
					console.error("Failed to fetch IT Role details", err);
					alert("Failed to fetch selected IT Role details.");
				});

			// Clear dropdown state
			$scope.itRoleSearchText = '';
			$scope.showITRoleDropdown = false;
		};
		
		$scope.dashboardCards = [];
		$scope.loadDashboardCards = function () {
		  $http.get(PluginHelper.getPluginRestUrl("rolemanagement/dashboard/cards"))  // Adjust endpoint
		    .then(function (response) {
		      $scope.dashboardCards = response.data;
		    })
		    .catch(function (error) {
		      console.error("Error loading dashboard cards", error);
		    });
		};
		$scope.loadDashboardCards();

		$scope.downloadRoles = function () {
		  const timeoutDuration = 30000; // Fallback if API fails to return timeout config
		  let timeoutPromise = null;
		
		  // Step 1: Fetch timeout value from the backend
		  $http.get(PluginHelper.getPluginRestUrl("rolemanagement/config/timeout"))
		    .then(function (res) {
		      const serverTimeout = res.data.timeout || timeoutDuration;
		
		      // Step 2: Create a timeout promise using $timeout
		      timeoutPromise = $timeout(function () {
		        $scope.showToast("Download request timed out", "error");
		        // Prevent the download call
		        downloadCancelled = true;
		      }, serverTimeout);
		
		      // Step 3: Prepare payload
		       const selectedRoleIds = $scope.selectedRoles.map(r => r.id);
		       console.log("selectedRoleIds ",selectedRoleIds);
				const payload = {
					roleIds: selectedRoleIds.length > 0 ? selectedRoleIds : null
				};
				
		
		      let downloadCancelled = false;
		
		      // Step 4: Call the API to get file content
		      $http.post(PluginHelper.getPluginRestUrl("rolemanagement/roles/download"), payload)
		        .then(function (response) {
		          if (downloadCancelled) return;
		
		          // Clear timeout
		          $timeout.cancel(timeoutPromise);
		
		          const csvContent = response.data;
				  console.log("CSV Content ",csvContent);
		          const blob = new Blob([csvContent], { type: 'application/csv;charset=utf-8;' });
		          const url = window.URL.createObjectURL(blob);
		
		          const link = document.createElement("a");
		          link.href = url;
		          link.download = "rolesExport.csv";
		          document.body.appendChild(link);
		          link.click();
		          document.body.removeChild(link);
		          window.URL.revokeObjectURL(url);
		
		          $scope.showToast("Download started", "success");
		        })
		        .catch(function (error) {
				  console.warn("error in get Role download data ",error);
		          $timeout.cancel(timeoutPromise);
		          $scope.showToast("Download failed", "error");
		        });
		    })
		    .catch(function (err2) {
			  console.warn("error in timeout setting ",err2);
		      $scope.showToast("Failed to get timeout value", "error");
		    });
		};

		


	}]);
	
	jQuery(document).on("click", "#uploadMenuItem, #dashboardMenuItem, #workgroupMenuItem", function () {

	    var scope = angular.element(document.querySelector('[ng-controller="RoleController"]')).scope();
	
	    var section = "";
	
	    if (this.id === "uploadMenuItem") {
	        section = "upload";
	    } 
	    else if (this.id === "dashboardMenuItem") {
	        section = "dashboard";
	    }
	    else if (this.id === "workgroupMenuItem") {
	        section = "workgroup";
	    }

	    scope.$apply(() => {
	        scope.setSection(section);
	    });
	});



// Safe outside-click listener to close workgroup popup
/*setTimeout(function () {
	document.addEventListener('click', function (event) {
		const popup = document.querySelector('.workgroup-popup');
		const icon = event.target.closest('.fa-user-group');
		const insidePopup = popup && popup.contains(event.target);

		const angularElem = document.querySelector('[ng-controller="RoleController as ctrl"]');
		if (!angularElem) return;

		const scope = angular.element(angularElem).scope();

		scope.$apply(function () {
			if (scope.showGroupMembers && !insidePopup && !icon) {
				scope.showGroupMembers = false;
			}
		});
	});
}, 0); */// Delay to ensure controller is initialized
