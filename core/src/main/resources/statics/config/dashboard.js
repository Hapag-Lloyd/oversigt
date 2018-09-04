// add ajax parameter to all forms
$(document).ready(function(){
	$('#save-gridster').off('click').click(function(e){
		var map = {};
		$('.gridster>ul>li').each(function(){
			map[$(this).attr('data-id')] = {
				"x":$(this).attr('data-col'),
				"y":$(this).attr('data-row'),
				"w":$(this).attr('data-sizex'),
				"h":$(this).attr('data-sizey')
			};
		});
		var currentElement = $(this);
		var postData = 'action=updateWidgetPositions&positions=' + encodeURIComponent(JSON.stringify(map));
		var location = window.location.href;
		if(location.indexOf('#') > -1) {
			location = location.substring(0, location.indexOf('#'));
		}
		location += '/config';
		$.ajax({
            type: 'POST',
            url: location,
            data: postData,
            error: function(jqXHR, textStatus, errorMessage) {
            	alert("The current layout could not be saved: "+errorMessage);
            },
            success: function(data) {
        		currentElement.slideUp();
            } 
        });
		return false;
	});
});
