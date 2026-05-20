/**
 * Reusable pagination bar for Role Management plugin pages.
 * Requires RoleApp (from roleManagement.js) to be loaded first.
 */
(function () {
	'use strict';

	angular.module('RoleApp').directive('paginationBar', ['$timeout', function ($timeout) {
		return {
			restrict: 'E',
			scope: {
				totalRecords: '=',
				page: '=',
				pageSize: '=',
				pageSizeOptions: '=?',
				onChange: '&',
				onRefresh: '&?'
			},
			template:
				'<div class="pagination-bar" ng-if="totalRecords > 0">' +
				'  <div class="pagination-info">' +
				'    <span class="pagination-info-label">Showing</span>' +
				'    <strong class="pagination-info-range">{{ startIndex }}&ndash;{{ endIndex }}</strong>' +
				'    <span class="pagination-info-label">of</span>' +
				'    <strong class="pagination-info-total">{{ totalRecords }}</strong>' +
				'  </div>' +
				'  <div class="pagination-page-size" ng-if="pageSizeOptions && pageSizeOptions.length">' +
				'    <label class="pagination-page-size-label">Rows per page</label>' +
				'    <select class="pagination-page-size-select" ng-model="pageSize"' +
				'            ng-options="s for s in pageSizeOptions"' +
				'            ng-change="changePageSize(pageSize)">' +
				'    </select>' +
				'  </div>' +
				'  <div class="pagination-actions">' +
				'    <button type="button" class="page-arrow page-arrow--icon" ng-if="hasRefresh()" ng-click="refresh()" title="Refresh" aria-label="Refresh">' +
				'      <i class="fas fa-arrows-rotate" aria-hidden="true"></i>' +
				'    </button>' +
				'    <div class="pagination-nav-group" role="group" aria-label="Pagination">' +
				'      <button type="button" class="page-arrow" ng-mousedown="onNavMouseDown()" ng-click="goToPage(1)" ng-disabled="page <= 1" title="First page" aria-label="First page">' +
				'        <i class="fas fa-angles-left" aria-hidden="true"></i>' +
				'      </button>' +
				'      <button type="button" class="page-arrow" ng-mousedown="onNavMouseDown()" ng-click="goPrev()" ng-disabled="page <= 1" title="Previous page" aria-label="Previous page">' +
				'        <i class="fas fa-chevron-left" aria-hidden="true"></i>' +
				'      </button>' +
				'      <span class="page-indicator" aria-live="polite">' +
				'        <input type="text" class="page-indicator-input" id="{{pageInputId}}"' +
				'               ng-blur="onPageInputBlur()" ng-keydown="onPageInputKeydown($event)"' +
				'               inputmode="numeric" pattern="[0-9]*" aria-label="Go to page" title="Enter page number and press Enter" />' +
				'        <span class="page-indicator-sep">/</span>' +
				'        <span class="page-indicator-total">{{ pageCount }}</span>' +
				'      </span>' +
				'      <button type="button" class="page-arrow" ng-mousedown="onNavMouseDown()" ng-click="goNext()" ng-disabled="page >= pageCount" title="Next page" aria-label="Next page">' +
				'        <i class="fas fa-chevron-right" aria-hidden="true"></i>' +
				'      </button>' +
				'      <button type="button" class="page-arrow" ng-mousedown="onNavMouseDown()" ng-click="goToPage(pageCount)" ng-disabled="page >= pageCount" title="Last page" aria-label="Last page">' +
				'        <i class="fas fa-angles-right" aria-hidden="true"></i>' +
				'      </button>' +
				'    </div>' +
				'  </div>' +
				'</div>',
			link: function (scope, element) {
				var skipNextBlur = false;
				var suppressPageWatch = false;

				scope.pageInputId = 'rm-pagination-page-input-' + scope.$id;

				if (!scope.pageSizeOptions) {
					scope.pageSizeOptions = [10, 25, 50, 100];
				}

				scope.hasRefresh = function () {
					return angular.isFunction(scope.onRefresh);
				};

				function currentPageNum() {
					return parseInt(scope.page, 10) || 1;
				}

				function getPageInputEl() {
					return document.getElementById(scope.pageInputId);
				}

				function getPageInputValue() {
					var el = getPageInputEl();
					return el ? el.value : '';
				}

				function parsePageValue(rawValue) {
					var p = parseInt(String(rawValue).trim(), 10);
					if (isNaN(p) || p < 1) {
						p = 1;
					} else if (p > scope.pageCount) {
						p = scope.pageCount;
					}
					return p;
				}

				function syncPageInput(pageNum) {
					var val = String(pageNum != null ? parseInt(pageNum, 10) : currentPageNum());
					var el = getPageInputEl();
					if (el && el.value !== val) {
						el.value = val;
					}
				}

				function recalc() {
					var size = parseInt(scope.pageSize, 10);
					if (isNaN(size) || size < 1) {
						size = scope.pageSizeOptions[0] || 25;
						scope.pageSize = size;
					}

					scope.pageCount = scope.totalRecords > 0
						? Math.ceil(scope.totalRecords / size)
						: 1;

					if (!scope.page || scope.page < 1) {
						scope.page = 1;
					}
					if (scope.page > scope.pageCount) {
						scope.page = scope.pageCount;
					}

					scope.startIndex = scope.totalRecords === 0
						? 0
						: ((currentPageNum() - 1) * size) + 1;

					var tentativeEnd = currentPageNum() * size;
					scope.endIndex = tentativeEnd > scope.totalRecords
						? scope.totalRecords
						: tentativeEnd;
				}

				function commitPage(p, notifyParent) {
					p = parseInt(p, 10);
					if (isNaN(p) || p < 1 || p > scope.pageCount) {
						return;
					}

					var prevPage = currentPageNum();
					skipNextBlur = true;
					suppressPageWatch = true;

					scope.page = p;
					recalc();
					syncPageInput(p);

					suppressPageWatch = false;

					if (notifyParent && p !== prevPage) {
						scope.onChange({ page: scope.page, pageSize: scope.pageSize });
					}

					$timeout(function () {
						syncPageInput(p);
					}, 0);
				}

				$timeout(function () {
					syncPageInput(currentPageNum());
				});

				scope.$watch('page', function (newPage) {
					if (suppressPageWatch) {
						return;
					}
					recalc();
					syncPageInput(newPage);
				});

				scope.$watchGroup(['totalRecords', 'pageSize'], function () {
					recalc();
					syncPageInput(currentPageNum());
				});

				scope.onNavMouseDown = function () {
					skipNextBlur = true;
				};

				scope.onPageInputBlur = function () {
					if (skipNextBlur) {
						skipNextBlur = false;
						syncPageInput(currentPageNum());
						return;
					}
					var p = parsePageValue(getPageInputValue());
					if (p !== currentPageNum()) {
						commitPage(p, true);
					} else {
						syncPageInput(currentPageNum());
					}
				};

				scope.onPageInputKeydown = function ($event) {
					if (!$event) {
						return;
					}
					if ($event.which === 13 || $event.key === 'Enter') {
						$event.preventDefault();
						$event.stopPropagation();
						skipNextBlur = true;
						commitPage(parsePageValue(getPageInputValue()), true);
					}
				};

				scope.goToPage = function (p) {
					commitPage(parseInt(p, 10), true);
				};

				scope.goPrev = function () {
					commitPage(currentPageNum() - 1, true);
				};

				scope.goNext = function () {
					commitPage(currentPageNum() + 1, true);
				};

				scope.changePageSize = function (pageSize) {
					var prevSize = parseInt(scope.pageSize, 10);
					var newSize = parseInt(pageSize, 10);
					if (isNaN(newSize) || newSize < 1) {
						newSize = scope.pageSizeOptions[0] || 25;
					}
					var prevPage = currentPageNum();

					scope.pageSize = newSize;
					skipNextBlur = true;
					suppressPageWatch = true;
					scope.page = 1;
					recalc();
					syncPageInput(1);
					suppressPageWatch = false;

					// Page size changes must reload data even when already on page 1
					// (commitPage only notifies when the page index changes).
					if (angular.isFunction(scope.onChange) && (newSize !== prevSize || prevPage !== 1)) {
						scope.onChange({ page: scope.page, pageSize: scope.pageSize });
					}

					$timeout(function () {
						syncPageInput(1);
					}, 0);
				};

				scope.refresh = function () {
					if (scope.hasRefresh()) {
						scope.onRefresh();
					}
				};
			}
		};
	}]);
})();
