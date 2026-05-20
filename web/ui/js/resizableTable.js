/**
 * Drag-to-resize columns on .role-table (workgroup list, roles home, bulk tables).
 * Requires RoleApp (load after roleManagement.js).
 */
(function () {
	'use strict';

	var MIN_COL_WIDTH = 72;
	var DEFAULT_COL_WIDTH = 140;

	function isNonResizableHeader(th) {
		if (!th || th.classList.contains('sticky-action-column')) {
			return true;
		}
		if (th.querySelector('input[type="checkbox"]') && !(th.textContent || '').trim()) {
			return true;
		}
		return false;
	}

	function columnIndex(table, th) {
		var headerRow = th.parentNode;
		if (!headerRow) {
			return -1;
		}
		return Array.prototype.indexOf.call(headerRow.children, th);
	}

	function applyColumnWidth(table, colIdx, widthPx) {
		var headerRow = table.querySelector('thead tr');
		if (!headerRow || colIdx < 0) {
			return;
		}
		var th = headerRow.children[colIdx];
		if (!th) {
			return;
		}
		var w = widthPx + 'px';
		th.style.width = w;
		th.style.minWidth = w;
		th.style.maxWidth = w;

		var rows = table.querySelectorAll('tbody tr');
		for (var r = 0; r < rows.length; r++) {
			var cell = rows[r].children[colIdx];
			if (cell) {
				cell.style.width = w;
				cell.style.minWidth = w;
				cell.style.maxWidth = w;
			}
		}
	}

	function clearResizeHandles(table) {
		var handles = table.querySelectorAll('.col-resize-handle');
		for (var i = 0; i < handles.length; i++) {
			handles[i].parentNode.removeChild(handles[i]);
		}
		table.removeAttribute('data-resizable-columns');
	}

	function initTable(table) {
		if (!table || !table.classList.contains('role-table')) {
			return;
		}

		clearResizeHandles(table);
		table.classList.add('role-table--resizable');
		table.setAttribute('data-resizable-columns', '1');

		if (!table.style.tableLayout) {
			table.style.tableLayout = 'fixed';
		}

		var ths = table.querySelectorAll('thead th');
		for (var i = 0; i < ths.length; i++) {
			(function (th) {
				if (isNonResizableHeader(th)) {
					return;
				}

				var idx = columnIndex(table, th);
				if (idx < 0) {
					return;
				}

				if (!th.style.width && !th.style.minWidth) {
					var initial = th.offsetWidth > 0 ? th.offsetWidth : DEFAULT_COL_WIDTH;
					applyColumnWidth(table, idx, Math.max(MIN_COL_WIDTH, initial));
				}

				var handle = document.createElement('span');
				handle.className = 'col-resize-handle';
				handle.setAttribute('aria-hidden', 'true');
				handle.title = 'Drag to resize column';
				th.appendChild(handle);

				handle.addEventListener('mousedown', function (e) {
					e.preventDefault();
					e.stopPropagation();

					var startX = e.pageX;
					var startWidth = th.offsetWidth;
					var colIdx = columnIndex(table, th);

					function onMove(ev) {
						var next = Math.max(MIN_COL_WIDTH, startWidth + (ev.pageX - startX));
						applyColumnWidth(table, colIdx, next);
					}

					function onUp() {
						document.removeEventListener('mousemove', onMove);
						document.removeEventListener('mouseup', onUp);
						document.body.classList.remove('col-resize-active');
					}

					document.body.classList.add('col-resize-active');
					document.addEventListener('mousemove', onMove);
					document.addEventListener('mouseup', onUp);
				});
			})(ths[i]);
		}
	}

	function findTable(el) {
		if (!el) {
			return null;
		}
		if (el.tagName === 'TABLE' && el.classList.contains('role-table')) {
			return el;
		}
		return el.querySelector('table.role-table');
	}

	angular.module('RoleApp').directive('resizableColumns', ['$timeout', function ($timeout) {
		return {
			restrict: 'A',
			link: function (scope, element) {
				var el = element[0];

				function refresh() {
					var table = findTable(el);
					if (table && table.offsetParent !== null) {
						initTable(table);
					}
				}

				$timeout(refresh, 0);

				scope.$watch(function () {
					var table = findTable(el);
					if (!table) {
						return '';
					}
					var head = table.querySelector('thead tr');
					var bodyRows = table.querySelectorAll('tbody tr').length;
					var colCount = head ? head.children.length : 0;
					return colCount + ':' + bodyRows;
				}, function () {
					$timeout(refresh, 0);
				});

				scope.$on('$destroy', function () {
					var table = findTable(el);
					if (table) {
						clearResizeHandles(table);
						table.classList.remove('role-table--resizable');
					}
				});
			}
		};
	}]);
})();
