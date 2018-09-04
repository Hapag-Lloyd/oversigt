# dashing-piechart

## Preview

![PieChart](https://raw.githubusercontent.com/wiki/jorgemorgado/dashing-piechart/piechart.png)

## Description

Simple [Dashing](http://shopify.github.com/dashing) widget (and associated job)
to render pie charts. Uses [Chart.js](http://www.chartjs.org/) library.

## Dependencies

Download the latest v2.x.x release of `Chart.bundle.min.js` from
[https://github.com/chartjs/Chart.js/releases](https://github.com/chartjs/Chart.js/releases)
and copy it into `assets/javascripts`. Make sure to remove any older versions
of Chart.js from the `assets/javascripts` folder.

NOTE: `dashing-piechart` is compatible with v2 of Chart.js. If you still
want to use the older version of Chart.js, you need to download the latest v1
of `Chart.min.js` and install [v1.0](https://github.com/jorgemorgado/dashing-piechart/releases/tag/v1.0)
of this widget. Although, remember that older versions are not maintained
anymore.

## Usage

Create the directory `widgets/pie_chart` and copy this widget's files
into that folder.

Add the following code on the desired dashboard:

```erb
<li data-row="1" data-col="3" data-sizex="1" data-sizey="1">
  <div data-id="piechart" data-view ="PieChart" data-title="Pie Chart" data-moreinfo=""></div>
</li>
```

Create your pie chart job `my_piechart_job.rb`:

```ruby
# Note: change this to obtain your chart data from some external source
labels = [ 'Jan', 'Feb', 'Mar' ]
data = [
  {
    data: Array.new(3) { rand(30) },
    backgroundColor: [
      '#F7464A',
      '#46BFBD',
      '#FDB45C',
    ],
    hoverBackgroundColor: [
      '#FF6384',
      '#36A2EB',
      '#FFCE56',
    ],
  },
]
options = { }

send_event('piechart', { labels: labels, datasets: data, options: options })
```

### Title Position

By default the title will be displayed on the top of the widget. If you
prefer to move it to the center, change the `$title-position` variable on the
SCSS file. Example:

```scss
$title-position:    center;
```

### Margins

You can also adjust the chart's margins: top, left, right and bottom. By
default they are all 0 (pixels) to use the whole available space. But if
needed you can change their value using the `data-` attributes. Example:

```erb
<li data-row="2" data-col="1" data-sizex="2" data-sizey="1">
  <div data-id="piechart" data-view ="PieChart" data-left-margin="5" data-top-margin="10"></div>
</li>
```

If not set, both right and bottom margins will be equal to left and top margins
respectively. This is likely what you want to keep the chart centered within
the widget. If not, set their values also using the `data-` attributes:

```erb
<li data-row="2" data-col="1" data-sizex="2" data-sizey="1">
  <div data-id="piechart" data-view ="PieChart" data-right-margin="10" data-bottom-margin="5"></div>
</li>
```

## Contributors

- [Jorge Morgado](https://github.com/jorgemorgado)

## License

This widget is released under the [MIT License](http://www.opensource.org/licenses/MIT).

## Other Chart.js Widgets

- [Bar Chart](https://github.com/jorgemorgado/dashing-barchart)
- [Bubble Chart](https://github.com/jorgemorgado/dashing-bubblechart)
- [Doughnut Chart](https://github.com/jorgemorgado/dashing-doughnutchart)
- [Line Chart](https://github.com/jorgemorgado/dashing-linechart)
- [Polar Chart](https://github.com/jorgemorgado/dashing-polarchart)
- [Radar Chart](https://github.com/jorgemorgado/dashing-radarchart)
