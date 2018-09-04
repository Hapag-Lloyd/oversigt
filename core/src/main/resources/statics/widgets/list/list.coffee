class Dashing.List extends Dashing.Widget

  ready: ->
    if @get('unordered')
      $(@node).find('ol').remove()
    else
      $(@node).find('ul').remove()
    @startAnimation()
    
  onData: (data) ->
    if not $(@node).find('ul,ol').is(':animated')
      that = this
      setTimeout((() -> that.startAnimation()), 10)
    if @get('hideSecondColumn')
      $(@node).find('.value').hide()
    else
      $(@node).find('.value').show()

  startAnimation: () ->
    #$(element).animate({ marginTop: -(heightUl - heightCut) }, durationInS, "ease", function () { /* complete */ })
    list = $(@node).find('ul,ol')
    cut  = $(@node).find('.cut')
    animatedHeight = list.height() - cut.height()
    if animatedHeight > 0
      that = this
      list.animate({marginTop: -animatedHeight}, animatedHeight * 100, 'swing', (()->list.animate({marginTop: 0}, animatedHeight * 100, 'swing', (()-> that.startAnimation()))))
