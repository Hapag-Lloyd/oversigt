// add ajax parameter to all forms
$(document).ready(function(){
	$('#save-gridster').off('click').click(function(e){
		var map = [];
		$('.gridster>ul>li').each(function(){
			map.push({
				"widgetId": $(this).attr('data-id'),
				"posX": $(this).attr('data-col'),
				"posY": $(this).attr('data-row'),
				"sizeX": $(this).attr('data-sizex'),
				"sizeY": $(this).attr('data-sizey')
			});
		});
		var currentElement = $(this);
		$.ajax({
            type: 'PUT',
            url: window.location.origin + '/api/v1/dashboards' + window.location.pathname + '/positions',
            data: JSON.stringify(map),
            error: function(jqXHR, textStatus, errorMessage) {
            	alert("The current layout could not be saved: " + errorMessage);
            },
            success: function(data) {
        		currentElement.slideUp();
            } 
        });
		return false;
	});
});
