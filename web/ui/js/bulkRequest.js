// Directive to capture selected file and call a scope function
RoleApp.directive('fileChange', [function () {
    return {
        restrict: 'A',
        scope: {
            fileChange: '&'
        },
        link: function (scope, element, attrs) {
            element.on('change', function (event) {
                var file = (event.target.files && event.target.files[0]) || null;
                if (!file) {
                    scope.$apply(function () {
                        scope.fileChange({ file: null, base64: null });
                    });
                    return;
                }

                

                var reader = new FileReader();
                // Fail fast if file exceeds 10MB
                var MAX_BYTES = 10 * 1024 * 1024; // 10MB
                if (file.size && file.size > MAX_BYTES) {
                    scope.$apply(function () {
                        scope.fileChange({
                            file: null,
                            base64: null,
                            error: "Selected file is too large. Maximum allowed size is 10MB."
                        });
                    });
                    return;
                }
                reader.onload = function (e) {
                    var contents = e.target.result; // this is the file text
                    var base64 = contents.split(',')[1] || null;
                    scope.$apply(function () {
                        scope.fileChange({
                            file: file,
                            base64: base64,
                            error: null
                        });
                    });
                };

                // For CSV/text
                //reader.readAsText(file);
                reader.readAsDataURL(file); // to get base64
            });
        }
    };
}]);
RoleApp.directive('ngRightClick', ['$parse', function($parse) {
    return function(scope, element, attrs) {
        var fn = $parse(attrs.ngRightClick);
        element.on('contextmenu', function(event) {
            event.preventDefault();
            scope.$apply(function() {
                fn(scope, { $event: event });
            });
        });
    };
}]);
RoleApp.controller('BulkRequestController', ['$scope', '$http', '$timeout',  function ($scope, $http, $timeout) {
 	
    $scope.config = {};
	
    $scope.initBatchRequest = function () {
        console.log('calling initBulkRequest');
        $http.get(PluginHelper.getPluginRestUrl("rolemanagement/batch/config"))
            .then(function success(response) {
                // Assume backend returns: { "enabled": true } or { "enabled": false }
                if (response.data) {
                    $scope.config = response.data;
                    console.log('config is ', $scope.config);
                }
            }, function error(err) {
                console.error("Failed to fetch initBulkRequest:", err);
            });
    };
    
    // --- default: table is visible, form is hidden ---
    $scope.showCreateBatch = false;
    $scope.uploadFile = null;
    $scope.uploadFileContents = null;
    $scope.fileError = null;

    $scope.batchConfig = {
        roleType: 'it',
        createRoleIfNotExist: false,
        performImpactAnalysisOnly: false
    };

    // Existing bulk requests + pagination
    $scope.batchRequestCurrentPage = 1;   // 1-based for UI
    $scope.batchRequestPageSize    = 10;  // default page size
    $scope.totalBatchRequestPages  = 0;
    $scope.totalBatchRequests      = 0;
    $scope.bulkRequests            = [];
    $scope.isBulkRequestsLoading   = false;
    $scope.bulkRequestFilterText   = '';
    $scope.showPendingOnly         = false;
    $scope.pendingCount            = 0;
    $scope.contextMenuVisible      = false;
    $scope.contextMenuTarget       = null;
    $scope.contextMenuPosition     = { x: 0, y: 0 };

    var contextMenuCloseHandler = function(evt) {
        if ($scope.contextMenuVisible) {
            var menu = document.querySelector('.context-menu');
            if (menu && evt && menu.contains(evt.target)) {
                return;
            }
            $scope.$applyAsync($scope.hideContextMenu);
        }
    };

    function updatePendingCount(requests) {
        var count = 0;
        var items = angular.isArray(requests) ? requests : [];
        for (var i = 0; i < items.length; i++) {
            if (items[i] && items[i].approvalStatus === 'Pending') {
                count++;
            }
        }
        $scope.pendingCount = count;
    }


    $scope.loadBulkRequests = function () {
        var pageIndex = $scope.batchRequestCurrentPage - 1; // backend is 0-based
        console.log('Loading bulk requests, page index:', pageIndex, 'size:', $scope.batchRequestPageSize);
        $scope.isBulkRequestsLoading = true;
        $http.get(PluginHelper.getPluginRestUrl("rolemanagement/batch/load"), {
            params: { page: pageIndex, size: $scope.batchRequestPageSize }
        }).then(function (resp) {
            $scope.bulkRequests           = resp.data.objects;
            $scope.totalBatchRequestPages = resp.data.totalPages;
            $scope.totalBatchRequests     = resp.data.count;
            updatePendingCount($scope.bulkRequests);
        }).finally(function () {
            $scope.isBulkRequestsLoading = false;
        });
    };


    $scope.togglePendingOnly = function () {
        $scope.showPendingOnly = !$scope.showPendingOnly;
    };

    $scope.filterBulkRequests = function (req) {
        if (!req) {
            return false;
        }
        if ($scope.showPendingOnly && req.approvalStatus !== 'Pending') {
            return false;
        }
        var term = ($scope.bulkRequestFilterText || '').toLowerCase();
        if (!term) {
            return true;
        }
        var fields = [req.fileName, req.owner, req.status, req.approvalStatus];
        for (var i = 0; i < fields.length; i++) {
            var value = fields[i];
            if (value && value.toString().toLowerCase().indexOf(term) !== -1) {
                return true;
            }
        }
        return false;
    };

    //TODO: even if we change the page size it is considering 10 as page size only. Need to fix it.
    $scope.onBatchPageChange = function(page, pageSize) {
        console.log('Batch page change: page=' + page + ', size=' + pageSize);
        $scope.batchRequestCurrentPage = page;      // 1-based
        $scope.batchRequestPageSize    = pageSize;  // from directive
        $scope.loadBulkRequests();
    };

    $scope.openBulkContextMenu = function(event, req) {
        event.preventDefault();
        event.stopPropagation();
        document.removeEventListener('click', contextMenuCloseHandler, true);
        $scope.contextMenuVisible = true;
        $scope.contextMenuTarget = req;
        $scope.contextMenuPosition = {
            x: event.clientX,
            y: event.clientY
        };
        document.addEventListener('click', contextMenuCloseHandler, true);
    };

    $scope.hideContextMenu = function() {
        $scope.contextMenuVisible = false;
        $scope.contextMenuTarget = null;
        document.removeEventListener('click', contextMenuCloseHandler, true);
    };

    $scope.$on('$destroy', function() {
        document.removeEventListener('click', contextMenuCloseHandler, true);
    });

    $scope.deleteBulkRequest = function(target, event) {
        if (event) {
            event.stopPropagation();
        }
        var req = target || $scope.contextMenuTarget;
        if (!req || !req.id) {
            $scope.hideContextMenu();
            return;
        }
        $http.delete(PluginHelper.getPluginRestUrl("rolemanagement/batch/" + req.id))
            .then(function() {
                if ($scope.showToast) {
                    $scope.showToast("Batch request deleted.", "success");
                }
                $scope.loadBulkRequests();
            }, function(err) {
                var msg = (err && err.data && err.data.message) ? err.data.message : "Failed to delete batch request.";
                if ($scope.showToast) {
                    $scope.showToast(msg, "error");
                } else {
                    console.error(msg);
                }
            }).finally(function() {
                $scope.hideContextMenu();
            });
    };



    if (!$scope.showCreateBatch) {
        $scope.loadBulkRequests();
    }

    $scope.$watch('showCreateBatch', function (newVal, oldVal) {
        if (newVal === false) {
            $scope.initBatchRequest();
            $scope.loadBulkRequests();
        }
    });

    $scope.prevPage = function () {
        if ($scope.pageIndex > 0) {
            $scope.pageIndex--;
            $scope.loadBulkRequests();
        }
    };

    $scope.nextPage = function () {
        if ($scope.pageIndex < $scope.totalPages - 1) {
            $scope.pageIndex++;
            $scope.loadBulkRequests();
        }
    };

    // --- Top buttons ---

    $scope.openCreateBatch = function () {
        $scope.showCreateBatch = true;
    };

    $scope.cancelCreateBatch = function () {
        $scope.showCreateBatch = false;
        $scope.previewHeaders = [];
        $scope.previewRows    = [];
        $scope.previewErrors  = [];
        $scope.uploadFile = null;
        $scope.uploadFileContents = null;
        $scope.fileError = null;
        $scope.batchCurrentPage = 1;
        var fileInput = document.getElementById('batchFileInput');
        if (fileInput) {
            fileInput.value = null;   // removes selected file
        }
    };

    $scope.showTemplateDownloadModal = false;
    $scope.templateDownload = { roleType: 'it' };
    $scope.templateDownloadInProgress = false;

    $scope.openTemplateDownloadModal = function () {
        $scope.templateDownload.roleType = 'it';
        $scope.showTemplateDownloadModal = true;
    };

    $scope.closeTemplateDownloadModal = function () {
        $scope.showTemplateDownloadModal = false;
    };

    /**
     * Downloads the Excel template for bulk role upload for the selected role type.
     */
    $scope.downloadTemplate = function () {
        var roleType = ($scope.templateDownload.roleType || 'it').toLowerCase();
        var fileName = roleType === 'business'
            ? 'Business_Role_BulkUpload_Template.xlsx'
            : 'IT_Role_BulkUpload_Template.xlsx';

        $scope.templateDownloadInProgress = true;
        $http.get(PluginHelper.getPluginRestUrl("rolemanagement/batch/downloadTemplate"), {
            params: { roleType: roleType }
        })
            .then(function (response) {
                var payload = response.data || {};
                var base64Content = payload.content;
                if (!base64Content) {
                    $scope.showToast('Download failed: empty file', 'error');
                    return;
                }

                var binary = atob(base64Content);
                var bytes = new Uint8Array(binary.length);
                for (var i = 0; i < binary.length; i++) {
                    bytes[i] = binary.charCodeAt(i);
                }
                if (bytes.length < 2 || bytes[0] !== 0x50 || bytes[1] !== 0x4B) {
                    console.warn('Template download: unexpected content prefix', bytes[0], bytes[1]);
                    $scope.showToast('Download failed: invalid file content', 'error');
                    return;
                }

                var blob = new Blob([bytes], {
                    type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
                });
                var url = window.URL.createObjectURL(blob);
                var link = document.createElement('a');
                link.href = url;
                link.download = payload.fileName || fileName;
                document.body.appendChild(link);
                link.click();
                document.body.removeChild(link);
                window.URL.revokeObjectURL(url);

                $scope.showTemplateDownloadModal = false;
                $scope.showToast('Download started', 'success');
            })
            .catch(function (error) {
                console.warn('error downloading bulk upload template', error);
                var msg = 'Download failed';
                if (error && error.data) {
                    if (angular.isString(error.data)) {
                        msg = error.data;
                    } else if (error.data.message) {
                        msg = error.data.message;
                    }
                }
                $scope.showToast(msg, 'error');
            })
            .finally(function () {
                $scope.templateDownloadInProgress = false;
            });
    };

    

    

    /**
     * Parse a single CSV line respecting quotes.
     * Handles cases like: "R03e, L07e" as ONE column.
     */
    function parseCsvLine(line) {
        var result = [];
        var current = '';
        var inQuotes = false;

        for (var i = 0; i < line.length; i++) {
            var ch = line[i];

            if (ch === '"') {
                // Handle escaped quotes ("")
                if (inQuotes && i + 1 < line.length && line[i + 1] === '"') {
                    current += '"';
                    i++; // skip next
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (ch === ',' && !inQuotes) {
                // comma that separates fields
                result.push(current);
                current = '';
            } else {
                current += ch;
            }
        }
        // push last value
        result.push(current);

        // trim spaces and strip surrounding quotes
        for (var j = 0; j < result.length; j++) {
            var val = result[j].trim();
            if (val.length >= 2 && val[0] === '"' && val[val.length - 1] === '"') {
                val = val.substring(1, val.length - 1);
            }
            result[j] = val;
        }

        return result;
    }

    $scope.onFileSelected = function (file, base64, error) {

        
        $scope.previewHeaders = [];
        $scope.previewRows    = [];
        $scope.previewErrors  = [];
        $scope.fileError = error || null;
        if (error) {
            $scope.uploadFile = null;
            $scope.uploadFileContents = null;
            return;
        }
        $scope.uploadFile = file;
        $scope.uploadFileContents = base64;

        if (!file) {
            return;
        }

        

        var reader = new FileReader();

        reader.onload = function (e) {
            var text = e.target.result || '';
            var errors = [];
            if (!text.trim().length === 0) {
                errors.push('Uploaded file is empty.');
                $scope.$apply(function () {
                    $scope.previewErrors = errors;
                });
            }

            // split into lines, remove empty
            var lines = text.split(/\r?\n/).filter(function (l) {
                return l.trim().length > 0;
            });

            if (!lines.length) {
                return;
            }

            // use the safe parser for each line
            var rows = lines.map(function (line) {
                return parseCsvLine(line);
            });

            var header = rows[0];
            var data   = rows.slice(1);

            var expectedCols = header.length;
            

            // validate: all rows have same number of columns as header
            data.forEach(function (row, idx) {
                if (row.length !== expectedCols) {
                    errors.push('Row ' + (idx + 2) +
                                ' has ' + row.length +
                                ' columns, expected ' + expectedCols + '.');
                }
            });

            // show at most 50 preview rows
            var maxPreview = 500;
            var previewData = data.slice(0, maxPreview);

            $scope.$apply(function () {
                $scope.previewHeaders = header;     
                $scope.previewRows    = previewData;
                $scope.previewErrors  = errors;
            });
        };

        reader.readAsText(file);
    };

    $scope.previewPage     = 1;
    $scope.previewPageSize = 25;


    $scope.onPreviewPageChange = function (page, pageSize) {
        $scope.previewPage = page;
        $scope.previewPageSize = pageSize;
    };

    $scope.goPrevPage = function () {
        if ($scope.previewPage > 1) {
            $scope.previewPage--;
        }
    };

    $scope.goNextPage = function () {
        if ($scope.previewPage * $scope.previewPageSize < $scope.previewRows.length) {
            $scope.previewPage++;
        }
    };

    $scope.deletePreviewRow = function (rowIndex) {
        if (rowIndex < 0 || rowIndex >= $scope.previewRows.length) {
            return;
        }

        $scope.previewRows.splice(rowIndex, 1);

        var maxPage = Math.max(1, Math.ceil($scope.previewRows.length / $scope.previewPageSize) || 1);
        if ($scope.previewPage > maxPage) {
            $scope.previewPage = maxPage;
        }
    };



    // --- Submit Create Batch Request ---

    $scope.submitBulkUpload = function () {
        var formData = new FormData();
        formData.append('file', $scope.uploadFile);
        formData.append('config', JSON.stringify($scope.batchConfig));

        var payload = {
            config: $scope.batchConfig,
            fileName: $scope.uploadFile ? $scope.uploadFile.name : null,
            fileContents: $scope.uploadFileContents,
            isRoleAdmin: $scope.config.isRoleAdmin
        };

        $http.post(PluginHelper.getPluginRestUrl("rolemanagement/batch/upload"), payload)
        .then(function (resp) {
            //alert('Batch request submitted.');
            $scope.showToast("Batch request submitted.", "success");
            $scope.showCreateBatch = false;
            $scope.loadBulkRequests();
        }, function (err) {
            $scope.showToast('Error submitting batch.'+ (err.data && err.data.message ? err.data.message : 'Unknown error'), "error");
        });
    };

    // initialize flags
    $scope.showDetails = false;
    $scope.selectedBatch = null;
    $scope.requestItemsCount = 0;
    $scope.requestItemsCurrentPage = 1;
    $scope.requestItemsPageSize = 25;
    $scope.showDifferenceModel = false;
    $scope.differencesLoading = false;
    $scope.roleDifferences = [];
    $scope.differenceRole = {};
    $scope.differencesError = null;

    

    // called when user clicks on a row
    $scope.viewDetails = function (req) {
        $scope.selectedBatch = req;   // full object from API
        $scope.showDetails = true;

        $scope.selectedBatch = angular.copy(req);

        var url = PluginHelper.getPluginRestUrl("rolemanagement/batch/" + req.id);
        $http.get(url).then(function (response) {
             $scope.selectedBatch.items = response.data.objects;
             $scope.requestItemsCount = response.data.count;
        }, function (error) {
            console.error("Error loading batch details", error);
        });
    };

    $scope.onRequestItemsPageChange = function(page, pageSize) {
        console.log('Request items page change: page=' + page + ', size=' + pageSize);
        $scope.requestItemsCurrentPage = page;      // 1-based
        $scope.requestItemsPageSize    = pageSize;  // from directive
        if (!$scope.selectedBatch) return;

        var total = $scope.requestItemsCount || 0;
        var currentItems = ($scope.selectedBatch.items || []);

        // If we have the full list locally, cache it for client-side paging.
        // Detect full list by comparing length to total count.
        if (!$scope.selectedBatch._allItems && currentItems.length === total) {
            // Store a shallow copy to preserve the master list
            $scope.selectedBatch._allItems = currentItems.slice(0);
        }

        var src = $scope.selectedBatch._allItems || currentItems;
        if (!src || !src.length) {
            $scope.selectedBatch.items = [];
            return;
        }

        // Compute slice boundaries and update visible items
        var start = Math.max(0, (page - 1) * pageSize);
        var end   = start + pageSize;
        if (start >= src.length) {
            // If out of range, clamp to last page
            start = Math.max(0, src.length - pageSize);
            end   = src.length;
        }
        $scope.selectedBatch.items = src.slice(start, end);
    };

    $scope.showRoleDifferences = function(item) {
        if (!item || !item.id) {
            return;
        }
        $scope.differenceRole = item;
        $scope.showDifferenceModel = true;
        $scope.differencesLoading = true;
        $scope.differencesError = null;
        $scope.roleDifferences = [];

        var url = PluginHelper.getPluginRestUrl("rolemanagement/batch/differences");
        var params = { itemId: item.id };
        if ($scope.selectedBatch && $scope.selectedBatch.id) {
            params.batchId = $scope.selectedBatch.id;
        }

        $http.get(url, { params: params }).then(function(response) {
            var data = response.data || {};
            var diffs = [];
            if (angular.isArray(data)) {
                diffs = data;
            } else if (angular.isArray(data.objects)) {
                diffs = data.objects;
            } else if (angular.isArray(data.differences)) {
                diffs = data.differences;
            }
            $scope.roleDifferences = diffs;
        }, function(error) {
            console.error("Error loading role differences", error);
            $scope.roleDifferences = [];
            $scope.differencesError = "Failed to load differences.";
            if ($scope.showToast) {
                $scope.showToast("Failed to load differences", "error");
            }
        }).finally(function () {
            $scope.differencesLoading = false;
        });
    };

    $scope.closeDifferenceModel = function () {
        $scope.showDifferenceModel = false;
        $scope.differenceRole = {};
        $scope.roleDifferences = [];
        $scope.differencesError = null;
        $scope.differencesLoading = false;
    };
    $scope.closeDiffereneModel = $scope.closeDifferenceModel;

    // back button from details -> list
    $scope.backToList = function () {
        $scope.showDetails = false;
        $scope.selectedBatch = null;
    };

    $scope.clearSearch = function () {
		  $scope.bulkRequestFilterText = "";
    };

    function triggerFileDownload(content, fileName, mimeType) {
        if (!content) {
            console.warn('No content available for download.');
            return;
        }
        var blob = new Blob([content], { type: mimeType || 'text/plain;charset=utf-8;' });
        var url = window.URL.createObjectURL(blob);
        var link = document.createElement('a');
        link.href = url;
        link.download = fileName || 'download.txt';
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        window.URL.revokeObjectURL(url);
    }

    function csvEscape(value) {
        var text = value === null || value === undefined ? '' : value.toString();
        if (text.indexOf('"') !== -1 || text.indexOf(',') !== -1 || text.indexOf('\n') !== -1) {
            return '"' + text.replace(/"/g, '""') + '"';
        }
        return text;
    }

    $scope.downloadBatchFile = function(batch) {
        var target = batch || $scope.selectedBatch;
        console.log("target for download ",target);
        if (!target) {
            return;
        }
        var content = target.fileContents || '';
        if (!content && target.items && target.items.length) {
            var rows = [];
            if (target.header) {
                rows.push(target.header);
            }
            for (var i = 0; i < target.items.length; i++) {
                var record = target.items[i].record || target.items[i].requestData;
                if (record) {
                    rows.push(record);
                }
            }
            content = rows.join('\n');
        }
        if (!content) {
            console.warn('Batch file is not available for download.');
            return;
        }
        var name = target.fileName || 'batch.csv';
        if (name.toLowerCase().indexOf('.csv') === -1) {
            name += '.csv';
        }
        triggerFileDownload(content, name, 'text/csv;charset=utf-8;');
    };

    //TODO: is there anything wrong in this download summary function? CSV Content is coming as expected but file is not downloading.
    $scope.downloadSummary = function(batch) {
        var target = batch || $scope.selectedBatch;
        $http.get(PluginHelper.getPluginRestUrl("rolemanagement/batch/downloadSummary/" + target.id))
		        .then(function (response) {
		          const csvContent = response.data;
				  console.log("CSV Content ",csvContent);
                  const blob = new Blob([csvContent], { type: 'application/csv;charset=utf-8;' });
		          const url = window.URL.createObjectURL(blob);
		
		          const link = document.createElement("a");
		          link.href = url;
                  var baseName = target.fileName || 'batch';
                  var summaryName = baseName.replace(/\.[^.]+$/, '') + '-summary.csv';
		          link.download = summaryName;
		          document.body.appendChild(link);
                  link.click(); 
		          document.body.removeChild(link);
		          window.URL.revokeObjectURL(url);
		
		          $scope.showToast("Download started", "success");
		        })
		        .catch(function (error) {
				  console.warn("error in get Role summary data ",error);
		          $scope.showToast("Download failed", "error");
		        });
    };

    $scope.canApproveReject = function() {
        return !!($scope.selectedBatch &&
            $scope.selectedBatch.approvalStatus === 'Pending' &&
            $scope.config &&
            $scope.config.isRoleAdmin);
    };

    $scope.showRejectConfirmation = false;
    $scope.rejectDialog = {
        batch: null,
        reason: '',
        error: ''
    };

    $scope.openRejectConfirmation = function(batch) {
        $scope.rejectDialog.batch = batch || null;
        $scope.rejectDialog.reason = '';
        $scope.rejectDialog.error = '';
        $scope.showRejectConfirmation = true;
    };

    $scope.closeRejectConfirmation = function() {
        $scope.showRejectConfirmation = false;
        $scope.rejectDialog.batch = null;
        $scope.rejectDialog.reason = '';
        $scope.rejectDialog.error = '';
    };

    function setApprovalStatus(batch, status, reason) {
        var payload = {
            batchId: batch ? batch.id : null,
            decision: status
        };

        if (reason) {
            payload.reason = reason;
            payload.rejectionReason = reason;
        }
        
        $http.post(PluginHelper.getPluginRestUrl("rolemanagement/batch/approveOrReject"), payload)
        .then(function (resp) {
            var apiStatus = resp.data && resp.data.status ? resp.data.status : '';
            if (apiStatus === 'Failed') {
                $scope.showToast('Error approving/rejecting batch.'+ (resp.data.error ? resp.data.error : 'Unknown error'), "error");
                $scope.showDetails = false;
                return;
            }
            $scope.showToast("Batch request " + status.toLowerCase() + ".", "success");
            $scope.showDetails = false;
            $scope.loadBulkRequests();
        }, function (err) {
            $scope.showToast('Error approving/rejecting batch.'+ (err.data && err.data.message ? err.data.message : 'Unknown error'), "error");
        });
    }

    $scope.approveSelectedBatch = function(batch) {
        setApprovalStatus(batch,'Approved');
    };

    $scope.confirmRejectSelectedBatch = function() {
        var batch = $scope.rejectDialog.batch;
        var reason = ($scope.rejectDialog.reason || '').trim();
        if (!reason) {
            $scope.rejectDialog.error = 'Rejection reason is required.';
            return;
        }

        $scope.rejectDialog.error = '';
        $scope.closeRejectConfirmation();
        setApprovalStatus(batch, 'Rejected', reason);
    };

    $scope.rejectSelectedBatch = function(batch, reason) {
        setApprovalStatus(batch,'Rejected', reason);
    };

 	 
}]);
