jQuery(document).ready(function () {
	if (window.location.href.includes("rolemanagement")) {
	
	    jQuery("ul.navbar-left > li:last").after(
	        '<li class="dropdown hidden-xs hidden-sm">' +
	        '   <a href="#" class="dropdown-toggle" role="menuitem" data-toggle="dropdown" aria-haspopup="true">' +
	        '       RME' +
	        '       <span class="sr-only">menu. Press ENTER or space to access submenu.</span>' +
	        '       <span role="presentation" aria-hidden="true" class="caret"></span>' +
	        '   </a>' +
	        '   <ul class="dropdown-menu" role="menu">' +
	        '       <li role="presentation" id="dashboardMenuItem">' +
	        '           <a href="#">Dashboard</a>' +
	        '       </li>' +
	        '       <li role="presentation" id="uploadMenuItem">' +
	        '           <a href="#">Bulk Upload</a>' +
	        '       </li>' +
			'       <li role="presentation" id="workgroupMenuItem">' +
	        '           <a href="#">Groups</a>' +
	        '       </li>' +
	        '   </ul>' +
	        '</li>'
	    );
    }
});
