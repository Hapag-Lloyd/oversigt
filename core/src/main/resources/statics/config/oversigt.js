function installOnlineSearch(selectorSearchInput, selectorSearchItems,
		urlToSearchData) {
	$(selectorSearchInput).focus(
			function(eventObject) {
				$.getJSON(urlToSearchData, function(data) {
					$(selectorSearchInput).attr('data-searchData',
							JSON.stringify(data));
				});
			});
	$(selectorSearchInput).keyup(
			function(eventObject) {
				var searchData = $.parseJSON($(selectorSearchInput).attr(
						'data-searchData'));
				var searchText = this.value.trim().toLowerCase();
				$(selectorSearchItems).each(function(){
					var id = $(this).attr('data-eventSource');
					var text = searchData[id].toLowerCase();
					if(text.indexOf(searchText) !== -1) {
						$(this).show();
					} else {
						$(this).hide();
						if ($(this).hasClass('active')) {
							var link = $(this).find('a').attr('href')
							if (link.startsWith('#')) {
								$(this).removeClass('active');
								$(link).removeClass('active');
								// TODO wenn das hier ausgeführt wurde,
								// den ersten aus der Liste anklicken
							}
						}
					}
				});
			});
}

function installFilterSearch(selectorSearchInput, selectorSearchItems,
		functionToText, onchange) {
	if (typeof functionToText === 'string' && functionToText.trim() !== '') {
		var selectorToText = functionToText;
		functionToText = function(e) {
			return e.find(selectorToText).text();
		};
	} else if (!$.isFunction(functionToText)) {
		functionToText = function(e) {
			return e.text();
		};
	}

	$(selectorSearchInput)
			.keyup(
					function(eventObject) {
						var searchText = this.value.trim().toLowerCase();
						$(selectorSearchItems).each(function() {
							var text = functionToText($(this));
							text = text.trim().toLowerCase();
							if (text.indexOf(searchText) !== -1) {
								$(this).show()
							} else {
								$(this).hide()
								if ($(this).hasClass('active')) {
									var link = $(this).find('a').attr('href')
									if (link.startsWith('#')) {
										$(this).removeClass('active');
										$(link).removeClass('active');
										// TODO wenn das hier ausgeführt wurde,
										// den ersten aus der Liste anklicken
									}
								}
							}
							;
						});
						if ($.isFunction(onchange)) {
							onchange(eventObject);
						}
					});
}

function getFilterTextByInputs(e) {
	var text = '';
	$(e).find('input, select, textarea').each(function(index, element) {
		element = $(element);
		if (!element.is('input[type="hidden"]')) {
			text += element.val() + "\n";
		}
	});
	return text;
}

// install syntax editor
function installSyntaxEditor(id, mode) {
	var editor = ace.edit(id);
	var inputElement = $('#hidden_' + id);
	editor.setTheme("ace/theme/eclipse");
	editor.setOption("maxLines", 30);
	editor.getSession().setUseWrapMode(true);
	editor.getSession().setMode("ace/mode/" + mode);
	editor.getSession().on("change", function() {
		inputElement.val(editor.getSession().getValue());
	});
}

// install JSON editors
function installJsonEditor(id) {
	var editorElement = $('#editor_' + id).get(0);
	var inputElement = $('#hidden_' + id);
	var editorJson = inputElement.val();
	var schemaJson = $('#schema_' + id).val();
	var startval = JSON.parse(editorJson);
	var schema = JSON.parse(schemaJson);
	var options = {
		ajax : true,
		collapsed : false,
		disable_collapse : true,
		disable_edit_json : true,
		disable_properties : true,
		iconlib : 'bootstrap3',
		no_additional_properties : true,
		object_layout : "grid",
		schema : schema,
		show_errors : 'always',
		startval : startval,
		required_by_default: true, 
		theme : 'bootstrap3'
	};
	var editor = new JSONEditor(editorElement, options);
	editor.on('change', function() {
		inputElement.val(JSON.stringify(editor.getValue()));
	});
}

// add ajax parameter to all forms
$(document).ready(function() {
	$('form[method="POST"]').each(function() {
		$(this).prepend('<input type="hidden" name="ajax" value="true"/>')
	});
});

// beautiful duration editor
/*
 * (function ($) { // Constructor for durationpicker 'class' var durationPicker =
 * function (element, options) { this.settings = options; this.template =
 * generate_template(this.settings); this.jqitem = $(this.template);
 * this.jqchildren = this.jqitem.children(); this.element = $(element);
 * this.setup(); this.resize(); this.moment = moment.duration("PT1H");
 * $(".durationpicker-duration").trigger('change'); var _self = this; };
 * 
 * durationPicker.prototype = { constructor: durationPicker, setup: function () {
 * this.element.before(this.jqitem); this.element.hide();
 * 
 * var factors = {}; var stages = getStages(this.settings); for (var i =
 * stages.length - 1; i >= 0; i--) { var factor =
 * this.settings[stages[i]].factor; factors[stages[i]] =
 * this.settings[stages[i]].factor; } $(this.element[0].id + "
 * .durationpicker-duration").on('change', {ths: this}, function (ev) { var
 * element = ev.data.ths.element; var value = ""; var seconds = 0;
 * $(this).parent().parent().find('input').each(function () { var input =
 * $(this); var val = 0; if (input.val() != null && input.val() != ""){ val =
 * input.val(); } var key = input.next().text(); value += val + key + ",";
 * seconds += val * factors[key]; }); value = value.slice(0, -1); value =
 * moment.duration(seconds, 'seconds').toISOString(); element.val(value); }); //
 * $(".durationpicker-duration").trigger(); window.addEventListener('resize',
 * this.resize); // enter current value var duration =
 * moment.duration(this.element.prop('defaultValue')); $(this.element[0].id + "
 * input.duration-seconds").prop('defaultValue', duration.seconds());
 * $(this.element[0].id + " input.duration-minutes").prop('defaultValue',
 * duration.minutes()); $(this.element[0].id + "
 * input.duration-hours").prop('defaultValue', duration.hours()); }, resize:
 * function() { //console.log(this.settings); if (!this.settings.responsive) {
 * return } var padding =
 * parseInt(this.jqitem.css('padding-left').split('px')[0]) +
 * parseInt(this.jqitem.css('padding-right').split('px')[0]); var minwidth =
 * padding; var minheight = padding; this.jqchildren.each(function () { var ths =
 * $(this); minwidth = minwidth + ths.outerWidth(); minheight = minheight +
 * ths.outerHeight(); }); if (this.jqitem.parent().width() < minwidth) {
 * this.jqchildren.each(function () { var ths = $(this); ths.css('display',
 * 'block'); }); this.jqitem.css('height', minheight) } else {
 * this.jqchildren.each(function () { var ths = $(this); ths.css('display',
 * 'inline-block'); }); } } };
 * 
 * 
 * $.fn.durationPicker = function(options){ if (options == undefined) { var
 * settings = $.extend(true, {}, $.fn.durationPicker.defaults, options); } else {
 * var settings = $.extend(true, {}, {classname: 'form-control', responsive:
 * true}, options); }
 * 
 * return this.each(function () { return new durationPicker(this, settings) }) };
 * 
 * function getStages(settings) { var stages = []; for (var key in
 * Object.keys(settings)){ if (['classname',
 * 'responsive'].indexOf(Object.keys(settings)[key]) == -1) {
 * stages.push(Object.keys(settings)[key]); } } return stages; }
 * 
 * function generate_template (settings) { var stages = getStages(settings); var
 * html = '<div class="durationpicker-container ' + settings.classname + '">';
 * for (var item in stages){ html += '<div
 * class="durationpicker-innercontainer"><input min="' +
 * settings[stages[item]]['min'] + '" max="' + settings[stages[item]]['max'] + '"
 * value="0" type="number" id="duration-' + stages[item] + '"
 * class="durationpicker-duration" ><span class="durationpicker-label">' +
 * settings[stages[item]]['label'] + '</span></div>'; } html += '</div>';
 * 
 * return html }
 * 
 * $.fn.durationPicker.defaults = { hours: { label: "hours", min: 0, max: 24,
 * factor: 60*60 }, minutes: { label: "minutes", min: 0, max: 60, factor: 60 },
 * seconds: { label: "seconds", min: 0, max: 60, factor: 1 }, classname:
 * 'form-control', responsive: true };
 * 
 * $.fn.durationPicker.Constructor = durationPicker;
 * 
 * })(jQuery);
 */

// Make select buttons better
$(document)
		.on(
				'blur',
				'.hlag-form-control-suggestions, .hlag-form-control-suggestions ~ select',
				function(eventObject) {
					var that;

					that = $(this);
					select = that.is('select') ? that : that.next();

					if (!select.is(':focus') && !select.prev().is(':focus')) {
						select.fadeOut();
					}
				});

$(document)
		.on(
				'focus',
				'.hlag-form-control-suggestions, .hlag-form-control-suggestions ~ select',
				function(eventObject) {
					$(this).next().finish().css('display', 'block');
				});

$(document).on('input', '.hlag-form-control-suggestions',
		function(eventObject) {
			var select, that, value;

			that = $(this);
			select = that.next();
			value = that.val().toLowerCase();

			select.prop('selectedIndex', 0);
			select.find('option').hide().filter(function(index, child) {
				return $(child).val().toLowerCase().indexOf(value) > -1;
			}).show();
		});

$(document).on(
		'keydown',
		'.hlag-form-control-suggestions',
		function(eventObject) {
			var select;

			if (eventObject.which === 38) { // KEY_UP
				select = $(this).next();
				if (select.prop('selectedIndex') < 0) {
					select.prop('selectedIndex',
							select.find('option').length - 1);
				} else {
					select[0].selectedIndex -= 1;
				}
				select.change();
			} else if (eventObject.which === 40) { // KEY_DOWN
				select = $(this).next();
				select[0].selectedIndex += 1;
				select.change();
			}
		});

$(document).on('change click', '.hlag-form-control-suggestions ~ select',
		function(eventObject) {
			$(this).prev().val($(this).val());
		});

$(document).on('focus', '.hlag-form-control-suggestions ~ select',
		function(eventObject) {
			$(this).prev().focus();
		});

// // make submit buttons to only call via ajax but not reload the page
// $(document).ready(function(){
// $('form').each(function(){
// var currentForm = $(this);
// var typeInput = $(this).find('input[name="type"]');
// if(typeInput && typeInput.attr('value') !== 'eventSource') {
// return;
// }
// $(this).find('button[type="submit"]').each(function(){
// var buttonValue = $(this).attr('name')+"="+$(this).val()+"&";
// $(this).on('click', function(ev){
// ev.preventDefault();
// var postContent = buttonValue+currentForm.serialize();
// $.ajax({
// type: currentForm.attr('method'),
// url: currentForm.attr('action'),
// data: postContent,
// error: function(jqXHR, textStatus, errorMessage) {
// alert(textStatus + ": " + errorMessage);
// location.reload();
// },
// success: function(data) {
// console.log(data);
// toastr.success('Saved event source
// "'+currentForm.find('input[name="name"]').val()+'"!')
// }
// });
// })
// })
// });
// });
