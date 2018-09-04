// Hide mouse after five seconds
(function() {
	var timeout;
	timeout = null;

	$(document).on('mousemove', function() {
		if (timeout !== null) {
			clearTimeout(timeout);
			$('body').removeClass('hide-mouse');
		}

		timeout = setTimeout(function() {
			timeout = null;
			$('body').addClass('hide-mouse');
		}, 5000);
	});
}());

// Resize dashboard using CSS transform to fit screen
(function() {
	var isZoomEnabled, ongridsterstart, onkeyup, onresize;

	isZoomEnabled = true;

	onresize = function(eventObject) {
		var container, css, gridster, newHeight, newWidth, originalHeight, originalWidth, screenHeight, screenWidth;

		css = {
			marginLeft : '',
			transform : ''
		};
		gridster = $('.gridster');

		if (isZoomEnabled) {
			$('body').css('overflow', 'hidden');

			originalHeight = gridster.height();
			originalWidth = gridster.width();
			screenHeight = $(window).height();
			screenWidth = $(window).width();

			newHeight = originalHeight * (screenWidth / originalWidth);
			newWidth = screenWidth;
			if (newHeight > screenHeight) {
				newHeight = screenHeight;
				newWidth = originalWidth * (screenHeight / originalHeight);
			}

			if (newWidth < originalWidth) {
				css.marginLeft = String((screenWidth - originalWidth) / 2)
						+ 'px';
				css.transform = 'scale(' + String(newWidth / originalWidth)
						+ ')';
			}
		}

		$('body').css('overflow', css.transform.length > 0 ? 'hidden' : '');
		gridster.css(css);
	};
	$(window).resize(onresize);

	ongridsterstart = function() {
		var e;

		e = $('.gridster > ul');
		if (e.length > 0 && typeof e.data().gridster === 'object') {
			onresize();
		} else {
			setTimeout(ongridsterstart, 100);
		}
	};
	ongridsterstart();

	onkeyup = function(eventObject) {
		if (String.fromCharCode(eventObject.which).toLowerCase() === 'z') {
			isZoomEnabled = !isZoomEnabled;
			onresize();
		}
	};
	$(window).keyup(onkeyup);
}());

// Toggling "Save this layout"
(function () {
	var onkeyup;
	
	onkeyup = function(eventObject) {
		if (String.fromCharCode(eventObject.which).toLowerCase() === 's') {
			$('#save-gridster').toggleClass('hide');
		}
	};
	$(window).keyup(onkeyup);
}());
