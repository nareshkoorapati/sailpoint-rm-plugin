angular.module('RoleApp', ['ui.bootstrap','sailpoint.comment','sailpoint.util','sailpoint.config','sailpoint.identity.account'])
  .config(['$httpProvider', function($httpProvider) {
    $httpProvider.defaults.xsrfCookieName = "CSRF-TOKEN";
  }])
  .controller('RoleController', ['$scope', '$http','spNotificationService', function($scope, $http,spNotificationService) {

	$scope.toast = {
	  visible: false,
	  message: '',
	  type: ''
	};
	
	$scope.showToast = function(message, type = 'success') {
	  $scope.toast.message = message;
	  $scope.toast.type = type;
	  $scope.toast.visible = true;
	
	  setTimeout(function() {
	    $scope.$apply(function() {
	      $scope.toast.visible = false;
	    });
	  }, 3000);
	};

    $scope.roles = [];
    $scope.selectedRoles = [];
    $scope.searchText = '';
    $scope.showSearchBy = false;
    $scope.searchColumn = 'name';
	$scope.Math = window.Math;
	
	$scope.showRoleModal = false;
	$scope.showEditRoleModal = false;

    // Pagination
    $scope.pageSize = 10;
    $scope.currentPage = 1;
    $scope.totalCount = 0;

    $scope.startIndex = 0;
    $scope.endIndex = 0;
    /**
     * @property string: currently active page.
     */
    $scope.activePage = 'home';

    $scope.loadData = function () {
      $http({
        method: 'GET',
        url: PluginHelper.getPluginRestUrl("rolemanagement/roles")
      }).then(function success(response) {
		console.log("role response ",response.data);
        $scope.roles = response.data;
        $scope.totalCount = $scope.roles.length;
        $scope.updatePageBounds();
      }, function error() {
        $scope.roles = [];
        $scope.totalCount = 0;
      });
    };

    $scope.updatePageBounds = function () {
      $scope.startIndex = ($scope.currentPage - 1) * $scope.pageSize;
      $scope.endIndex = Math.min($scope.startIndex + $scope.pageSize, $scope.totalCount);
    };

    $scope.changePageSize = function () {
      $scope.currentPage = 1;
      $scope.updatePageBounds();
    };

    $scope.goToFirstPage = function () {
      $scope.currentPage = 1;
      $scope.updatePageBounds();
    };

    $scope.goToPreviousPage = function () {
      if ($scope.currentPage > 1) {
        $scope.currentPage--;
        $scope.updatePageBounds();
      }
    };

    $scope.goToNextPage = function () {
      if ($scope.currentPage * $scope.pageSize < $scope.totalCount) {
        $scope.currentPage++;
        $scope.updatePageBounds();
      }
    };

    $scope.goToLastPage = function () {
      $scope.currentPage = Math.ceil($scope.totalCount / $scope.pageSize);
      $scope.updatePageBounds();
    };

    $scope.goToPage = function () {
      if ($scope.currentPage < 1) $scope.currentPage = 1;
      if ($scope.currentPage > Math.ceil($scope.totalCount / $scope.pageSize)) {
        $scope.currentPage = Math.ceil($scope.totalCount / $scope.pageSize);
      }
      $scope.updatePageBounds();
    };

    $scope.toggleAll = function () {
      const visibleRoles = $scope.roles.slice($scope.startIndex, $scope.endIndex);
      const allSelected = visibleRoles.every(role => role.selected);
      visibleRoles.forEach(role => role.selected = !allSelected);
      $scope.updateSelection();
    };

    $scope.updateSelection = function () {
      $scope.selectedRoles = $scope.roles.filter(role => role.selected);
    };

    $scope.toggleSearchBy = function () {
      $scope.showSearchBy = !$scope.showSearchBy;
    };


    /*$scope.viewRole = function(role) {
	  $http.get(PluginHelper.getPluginRestUrl("rolemanagement/role/" + encodeURIComponent(role.id)))
	    .then(function(response) {
	      $scope.selectedRole = response.data;
	      $scope.showRoleModal = true;
	      $scope.setTab('details');
	      $scope.groupITRolesByEntitlement(); // Call here
	    })
	    .catch(function(err) {
	      console.error("Failed to load role details", err);
	      alert("Error loading role details");
	    });
	};*/
	
	// Reusable function to fetch role details
	$scope.loadRoleDetails = function(roleId, onSuccess) {
	  $http.get(PluginHelper.getPluginRestUrl("rolemanagement/role/" + encodeURIComponent(roleId)))
	    .then(function(response) {
	      $scope.selectedRole = response.data;
	      console.log('role data ',$scope.selectedRole);
	      $scope.collapsedRoles = {};
	      $scope.collapseAllRoles();
	      $scope.groupITRolesByEntitlement(); // Optional if needed for both view/edit	      
	      if (onSuccess) onSuccess(response.data);
	    })
	    .catch(function(err) {
	      console.error("Failed to load role details", err);
	      alert("Error loading role details");
	    });
	};

	// View role modal
	$scope.viewRole = function(role) {
	  $scope.loadRoleDetails(role.id, function() {
	    $scope.showRoleModal = true;
	    $scope.setTab('details');
	  });
	};
	
	// Edit role modal (new usage example)
	$scope.editRole = function(role) {
		console.log('Edit role called ');
	  $scope.loadRoleDetails(role.id, function() {
	    $scope.showEditRoleModal = true;
	    $scope.setTab('edit-details');
	  });
	};


	$scope.closeRoleModal = function() {
	  $scope.showRoleModal = false;
	};
	
	$scope.setTab = function(tab) {
	  $scope.activeTab = tab;
	};
	
	$scope.isTab = function(tab) {
	  return $scope.activeTab === tab;
	};
    
    $scope.setPage = function(pageName){
		$scope.activePage = pageName;
	}
	$scope.isSet = function(pageName){
		console.log('checking ',pageName,'is set ',$scope.activePage == pageName);
		return ($scope.activePage == pageName);
	}
	
	
	$scope.workgroupMembers = [];
	$scope.workgroupSearch = '';
	$scope.workgroupPageSize = 5;
	$scope.workgroupCurrentPage = 1;
	$scope.workgroupTotalPages = 1;
	
	$scope.loadGroupMembers = function($event, ownerId) {
		console.log('calling loadGroupMembers');
	  if ($event && $event.stopPropagation) $event.stopPropagation();
	
	  $http({
	    method: "GET",
	    url: PluginHelper.getPluginRestUrl("rolemanagement/workgroup/" + ownerId)
	  }).then(function success(res) {
	    $scope.workgroupMembers = res.data;
	    $scope.workgroupMembersLoaded = true;
	
	    // Initialize pagination if not already
	    $scope.workgroupSearch = '';
	    $scope.workgroupCurrentPage = 1;
	    $scope.updateWorkgroupPagination();
	  }, function error(err) {
	    console.error("Failed to fetch group members", err);
	    alert("Could not load group members.");
	  });
	};

	$scope.switchToWorkgroupTab = function() {
	  $scope.setTab('workgroup');
	  if (!$scope.workgroupMembersLoaded) {
	    $scope.loadGroupMembers(null, $scope.selectedRole.ownerId);
	  }
	};
	
	$scope.updateWorkgroupPagination = function() {
	  const totalItems = $scope.filteredWorkgroupMembers().length;
	  $scope.workgroupTotalPages = Math.ceil(totalItems / $scope.workgroupPageSize);
	};
	
	$scope.filteredWorkgroupMembers = function() {
	  if (!Array.isArray($scope.workgroupMembers)) return [];
	
	  const keyword = ($scope.workgroupSearch || '').toLowerCase();
	
	  return $scope.workgroupMembers.filter(function(member) {
	    return (member.name || '').toLowerCase().includes(keyword) ||
	           (member.displayName || '').toLowerCase().includes(keyword) ||
	           (member.employeeid || '').toLowerCase().includes(keyword);
	  });
	};

	
	$scope.paginatedWorkgroupMembers = function() {
	  const start = ($scope.workgroupCurrentPage - 1) * $scope.workgroupPageSize;
	  const end = start + $scope.workgroupPageSize;
	  return $scope.filteredWorkgroupMembers().slice(start, end);
	};
	
	$scope.changeWorkgroupPage = function(delta) {
	  const newPage = $scope.workgroupCurrentPage + delta;
	  if (newPage >= 1 && newPage <= $scope.workgroupTotalPages) {
	    $scope.workgroupCurrentPage = newPage;
	  }
	};

	/* IT Roles Code */
	$scope.groupITRolesByEntitlement = function() {
	  const groups = {};
	  ($scope.selectedRole.itRoles || []).forEach(function(role) {
	    const entitlement = role.entitlement || 'Unknown';
	    if (!groups[entitlement]) {
	      groups[entitlement] = [];
	    }
	    groups[entitlement].push(role);
	  });
	  $scope.groupedITRoles = groups;
	};
	
	$scope.collapsedITRoles = {};

	$scope.toggleCollapse = function(entitlement) {
	  $scope.collapsedITRoles[entitlement] = !$scope.collapsedITRoles[entitlement];
	};

	/* Role Update Changes */
	
	$scope.closeEditRoleModal = function() {
	  $scope.showEditRoleModal = false;
	};
	
	// Save updated role
	$scope.saveEditedRole = function() {
		console.log('updating role ',$scope.selectedRole);
		spNotificationService.addNotification("Saving the role", spNotificationService.STATUS.SUCCESS);
    	spNotificationService.triggerDirective();
	  $http({
	    method: 'POST',
	    url: PluginHelper.getPluginRestUrl("rolemanagement/update"),
	    data: $scope.selectedRole
	  }).then(function success(response) {
	    alert("Role updated successfully!");
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
	$scope.markEntitlementForDeletion = function(entitlement) {
	  entitlement._deleted = true;
	};
	
	// Load attributes when application changes
	$scope.loadAttributesForApp = function(application) {
	  $scope.newEntitlement.attribute = '';
	  $scope.newEntitlement.value = '';
	};
	
	// Load entitlement values when attribute changes
	$scope.loadEntitlementsForAttribute = function(app, attribute) {
	  $scope.newEntitlement.value = '';
	};
	
	// Add new entitlement to role
	$scope.addNewEntitlement = function() {
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
	
	$scope.onITRoleSearchChange = function() {
	  $scope.itRoleSearchTriggered = false;
	
	  if (itRoleSearchTimeout) clearTimeout(itRoleSearchTimeout);
	
	  if ($scope.itRoleSearchText.length >= 3) {
	    $scope.fetchITRoles($scope.itRoleSearchText);
	  } else if ($scope.itRoleSearchText.length > 0) {
	    itRoleSearchTimeout = setTimeout(function() {
	      $scope.$apply(function() {
	        $scope.fetchITRoles($scope.itRoleSearchText);
	      });
	    }, 2000);
	  } else {
	    $scope.itRoleSearchResults = [];
	    $scope.showITRoleDropdown = false;
	  }
	};

	$scope.fetchITRoles = function(query) {
	  $http.get(PluginHelper.getPluginRestUrl("rolemanagement/itroles/search?q=" + encodeURIComponent(query)))
	    .then(function(response) {
	      $scope.itRoleSearchResults = response.data || [];
	      $scope.showITRoleDropdown = true;
	      $scope.itRoleSearchTriggered = true;
	    })
	    .catch(function(err) {
	      console.error("Failed to search IT Roles", err);
	      $scope.itRoleSearchResults = [];
	      $scope.showITRoleDropdown = true;
	      $scope.itRoleSearchTriggered = true;
	    });
	};

	
	$scope.selectITRole = function(role) {
	  $http.get(PluginHelper.getPluginRestUrl("rolemanagement/itroles/details/" + encodeURIComponent(role.id)))
	    .then(function(response) {
	      const newITRole = response.data;
	      newITRole.isNew = true;  // mark it as newly added
	      if (!$scope.selectedRole.itRoles) {
	        $scope.selectedRole.itRoles = [];
	      }
	      $scope.selectedRole.itRoles.push(newITRole);
	    })
	    .catch(function(err) {
	      console.error("Failed to fetch IT Role details", err);
	      alert("Failed to fetch selected IT Role details.");
	    });
	
	  // Clear dropdown state
	  $scope.itRoleSearchText = '';
	  $scope.showITRoleDropdown = false;
	};






  }]);
  
  // Safe outside-click listener to close workgroup popup
	setTimeout(function () {
	  document.addEventListener('click', function(event) {
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
	}, 0); // Delay to ensure controller is initialized
