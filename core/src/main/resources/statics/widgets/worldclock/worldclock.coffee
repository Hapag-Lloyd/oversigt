# Requires moment, moment-timezone and moment-timezone-data be added to the
# assets. And in the html view:
#
# data-title: Display name for location
# data-timezone: string designation for timezone (http://momentjs.com/timezone/data/)
# data-view: Worldclock
# data-id: whatever
class Dashing.Worldclock extends Dashing.Widget

  ready: ->
    dateFormat = @get('dateformat')
    timeFormat = @get('timeformat')
    
    if !timeFormat then $(@node).find('[data-bind=time]').css('display', 'none')
    if !dateFormat then $(@node).find('[data-bind=date]').css('display', 'none')
  	
    if @get('titleleft')
    	$(@node).find('.title').css('writing-mode','sideways-lr').css('float', 'left').css('height', '100%').css('margin', 'auto');

    setInterval(@startTime, 500)

  startTime: =>
    dateFormat = @get('dateformat')
    timeFormat = @get('timeformat')
    
    localMoment = moment.locale(@get('language'))
    today = moment(new Date()).tz(@get('timezone'))
    @set('time', today.format(timeFormat))
    @set('date', today.format(dateFormat))