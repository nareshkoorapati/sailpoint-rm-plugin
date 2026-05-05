jQuery(document).ready(function () {
    jQuery("ul.navbar-left > li:last").after(
        '<li class="dropdown hidden-xs hidden-sm">' +
        '   <a href="#" class="dropdown-toggle" role="menuitem" data-toggle="dropdown" aria-haspopup="true">' +
        '       RME' +
        '       <span class="sr-only">menu. Press ENTER or space to access submenu.</span>' +
        '       <span role="presentation" aria-hidden="true" class="caret"></span>' +
        '   </a>' +
        '   <ul class="dropdown-menu" role="menu">' +
        '       <li role="presentation" id="uploadMenuItem" ng-click="setSection(\'upload\')">' +
        '           <a href="#">Upload</a>' +
        '       </li>' +
        '   </ul>' +
        '</li>'
    );
});
