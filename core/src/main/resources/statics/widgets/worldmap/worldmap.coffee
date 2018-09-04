class Dashing.Worldmap extends Dashing.Widget

  ready: ->
    container = $(@node).parent()

    $node = $(@node)
    width = $node.width()
    height = $node.height()

    projection = d3.geo.equirectangular()
      .rotate([@get('center_long'), @get('center_lat')])
      .center([@get('translate_x'), @get('translate_y')])
      #.scale(Math.min(width*@get('scale_width'), height*@get('scale_height')))
      .scale(width*@get('zoom')/100)
      .translate([width/2, height/2])

    path = d3.geo.path()
      .projection(projection)

    #create base svg object
    svg = d3.select(@node).append("svg")
      .attr("width", width)
      .attr("height", height)

    #add background
    svg.append("rect")
      .attr("class", "background")
      .attr("width", width)
      .attr("height", height)

    g = svg.append("g")

    #load map json
    # https://localhost/assets/widgets/worldmap/world.json
    # https://localhost/assets/widgets/worldmap/world_wo_antarctica.json
    d3.json("assets/widgets/worldmap/"+@get('worldmap')+".json", (error, world) ->
      #add country outlines
      g.append("g")
          .attr("id", "countries")
        .selectAll("path")
          .data(topojson.feature(world, world.objects.countries).features)
        .enter().append("path")
          .attr("d", path)

      #add country borders
      g.append("path")
          .datum(topojson.mesh(world, world.objects.countries, (a, b) -> a != b ))
          .attr("id", "country-borders")
          .attr("d", path)
    )



  onData: (data) ->
    if (Dashing.widget_base_dimensions)
      container = $(@node).parent()

      $node = $(@node)
      width = $node.width()
      height = $node.height()

      projection = d3.geo.equirectangular()
        .rotate([@get('center_long'), @get('center_lat')])
        .center([@get('translate_x'), @get('translate_y')])
        .scale(width*@get('zoom')/100)
        .translate([width/2, height/2])


      #on each update grab the existing svg node
      svg = d3.select(@node).select('svg')

      #select all the points on the map and merge with current data
      circle = svg.selectAll("circle")
        .data(data.points, (d) -> d.id )

      defaultPointSize = @get('defaultPointSize')
      #for each new point, add a svg circle
      #I've left commented code to animate and base the circle radius on an optional size parameter
      circle.enter().append("circle")
        #.attr("r", (d) -> if d.size then 5 + parseInt(d.size,10)/(100*1024*1024) else 5)
        .attr("r", (d) -> if d.size then d.size else defaultPointSize)
        #.attr('r', Math.min(.008*width, @get('maxPointSize')))
        .attr("class", (d) -> "point")
        .attr("style", (d) -> (if d.fill then 'fill:'+d.fill+';' else '')+(if d.stroke then 'stroke:'+d.stroke+';' else ''))
        .attr("transform", (d) -> "translate(" + projection([d.lon,d.lat]) + ")" )
        # .transition()
        #   .duration(1000)
        #   .attr('r', 5)
        # .transition()
        #   .delay(1000)
        #   .duration(30000)
        #   .style('opacity', .4)

      #remove points no longer in data set
      circle.exit().remove()

      #reorder points based on id
      #if new points will always have higher ids than existing points, this may not be necessary
      circle.order()

