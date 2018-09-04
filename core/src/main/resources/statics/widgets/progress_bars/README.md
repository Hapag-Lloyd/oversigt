# Progress Bar Widget

## Description

A widget made for [Dashing](http://shopify.github.io/dashing/). This widget shows multiple animated progress bars and reacts dynamically to new information being passed in. Anything with a current state and with a projected max/goal state can easily be represented with this widget. Some sample ideas would be to show progress, completion, capacity, load, fundraising, and much more.

## Features

* Animating progress bars - Both the number and bar will grow or shrink based on new data that is being passed to it.
* Responsive Design - Allows the widget to be resized to any height or width and still fit appropriately. The progress bars will split up all available space amongst each other, squeezing in when additional progress bars fill the widget.
* Easy Customization - Change the base color in one line in the scss and have the entire widget color scheme react. The font size and progress bar size are handled by a single magic variable in the scss that will scale each bar up proportionally.

## Preview

![A screenshot showing multiple variations of the widget](http://i.imgur.com/jYjyTzA.png)A screenshot showing multiple variations of the widget. A live demo is available [here](http://progress-bar-demo.herokuapp.com/sample)

## Dependencies

Needs a job that sends data to the widget.

## Usage

With this sample widget code in your dashboard:  
```html
<li data-row="1" data-col="1" data-sizex="2" data-sizey="1">
  <div data-id="progress_bars" data-view="ProgressBars" data-title="Project Bars"></div>
</li>
```
You can send an event through a job like the following:
`send_event( 'progress_bars', {title: "", progress_items: []} )`

progress_items is an array of hashes that follow this design:
`{name: <value>, progress: <value>}`
The 'name' key can be any unique string that describes the bar. The 'progress' variable is a value from 0-100 that will represent the percentage of the bar that should be filled. Valid inputs include: `24, "24", "24%", 24.04`

Sending a request to a web service for a JSON response or reading from a file can produce this information easily.
