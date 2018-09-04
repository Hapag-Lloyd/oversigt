class Dashing.RemoteReload extends Dashing.Widget

  # Wait a short while between reloads.  Sometimes dashing will send a `onData()` event
  # right when we startup, so without this we can get into a reload-forever loop.
  minTimesBetweenReload: 1000 * 5 # 5 seconds

  ready: ->
    @lastLoad = Date.now()
    @initialized = true


  # Handle new data from Dashing.
  onData: (data) ->
    if !@initialized or (Date.now() < (@lastLoad + @minTimesBetweenReload))
        console.log "Too soon to reload.  Wait #{(@lastLoad + @minTimesBetweenReload - Date.now()) / 1000} seconds."
    else if data.dashboards
        dashboard = location.href
        dashboard = dashboard.substr(dashboard.lastIndexOf('/') + 1)
        if dashboard in data.dashboards
            location.reload()
#    else if data.url
#        location.replace(data.url)
#    else
#        location.reload()
