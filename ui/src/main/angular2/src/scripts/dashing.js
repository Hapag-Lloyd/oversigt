(function() {
  var lastEvents, source, widgets,
    __extends = function(child, parent) { for (var key in parent) { if (__hasProp.call(parent, key)) child[key] = parent[key]; } function ctor() { this.constructor = child; } ctor.prototype = parent.prototype; child.prototype = new ctor(); child.__super__ = parent.prototype; return child; },
    __hasProp = {}.hasOwnProperty,
    __bind = function(fn, me){ return function(){ return fn.apply(me, arguments); }; };

  Batman.Filters.prettyNumber = function(num) {
    if (!isNaN(num)) {
      return num.toString().replace(/\B(?=(\d{3})+(?!\d))/g, ",");
    }
  };

  Batman.Filters.dashize = function(str) {
    var dashes_rx1, dashes_rx2;
    dashes_rx1 = /([A-Z]+)([A-Z][a-z])/g;
    dashes_rx2 = /([a-z\d])([A-Z])/g;
    return str.replace(dashes_rx1, '$1_$2').replace(dashes_rx2, '$1_$2').replace(/_/g, '-').toLowerCase();
  };

  Batman.Filters.shortenedNumber = function(num) {
    if (isNaN(num)) {
      return num;
    }
    if (num >= 1000000000) {
      return (num / 1000000000).toFixed(1) + 'B';
    } else if (num >= 1000000) {
      return (num / 1000000).toFixed(1) + 'M';
    } else if (num >= 1000) {
      return (num / 1000).toFixed(1) + 'K';
    } else {
      return num;
    }
  };

  window.Dashing = (function(_super) {
    __extends(Dashing, _super);

    function Dashing() {
      return Dashing.__super__.constructor.apply(this, arguments);
    }

    Dashing.on('reload', function(data) {
      return window.location.reload(true);
    });

    Dashing.root(function() {});

    return Dashing;

  })(Batman.App);

  Dashing.params = Batman.URI.paramsFromQuery(window.location.search.slice(1));

  Dashing.Widget = (function(_super) {
    __extends(Widget, _super);

    function Widget() {
      this.onData = __bind(this.onData, this);
      this.receiveData = __bind(this.receiveData, this);
      var type, _base, _name;
      this.constructor.prototype.source = Batman.Filters.underscore(this.constructor.name);
      Widget.__super__.constructor.apply(this, arguments);
      this.mixin($(this.node).data());
      (_base = Dashing.widgets)[_name = this.id] || (_base[_name] = []);
      Dashing.widgets[this.id].push(this);
      type = Batman.Filters.dashize(this.view);
      $(this.node).addClass("widget widget-" + type + " " + this.id);
    }

    Widget.accessor('updatedAtMessage', function() {
      var hours, minutes, timestamp, updatedAt;
      if (updatedAt = this.get('updatedAt')) {
        timestamp = new Date(updatedAt * 1000);
        hours = timestamp.getHours();
        minutes = ("0" + timestamp.getMinutes()).slice(-2);
        return "Last updated at " + hours + ":" + minutes;
      }
    });

    Widget.prototype.on('ready', function() {
      var lastData;
      Dashing.Widget.fire('ready');
      lastData = Dashing.lastEvents[this.id];
      if (lastData) {
        return this.receiveData(lastData);
      }
    });

    Widget.prototype.receiveData = function(data) {
      var clone;
      if (data.error) {
        if ($(this.node).find('.eventSourceError').length < 1) {
          $(this.node).append('<div class="eventSourceError"><h1>Error</h1><p></p></div>');
        }
        $(this.node).parent().addClass('error');
        return $(this.node).find('.eventSourceError p').text(data.errorMessage);
      } else {
        $(this.node).parent().removeClass('error');
        clone = $(this.node).clone().insertAfter(this.node).css('opacity', 0.999999);
        this.mixin(data);
        return this.onData(data, setTimeout((function() {
          return clone.remove();
        }), 1000));
      }
    };

    Widget.prototype.onData = function(data) {};

    return Widget;

  })(Batman.View);

  Dashing.AnimatedValue = {
    get: Batman.Property.defaultAccessor.get,
    set: function(k, to) {
      var num, num_interval, timer, up;
      if ((to == null) || isNaN(to)) {
        return this[k] = to;
      } else {
        timer = "interval_" + k;
        num = !isNaN(this[k]) && (this[k] != null) ? this[k] : 0;
        if (!(this[timer] || num === to)) {
          to = parseFloat(to);
          num = parseFloat(num);
          up = to > num;
          num_interval = Math.abs(num - to) / 90;
          this[timer] = setInterval((function(_this) {
            return function() {
              num = up ? Math.ceil(num + num_interval) : Math.floor(num - num_interval);
              if ((up && num > to) || (!up && num < to)) {
                num = to;
                clearInterval(_this[timer]);
                _this[timer] = null;
                delete _this[timer];
              }
              _this[k] = num;
              return _this.set(k, to);
            };
          })(this), 10);
        }
        return this[k] = num;
      }
    }
  };

  Dashing.widgets = widgets = {};

  Dashing.lastEvents = lastEvents = {};

  Dashing.debugMode = false;

  source = new EventSource('http://localhost/events?dashboard=' + window.location.pathname.substr(1));

  source.addEventListener('open', function(e) {
    return console.log("Connection opened", e);
  });

  source.addEventListener('error', function(e) {
    console.log("Connection error", e);
    if (e.currentTarget.readyState === EventSource.CLOSED) {
      console.log("Connection closed");
      return setTimeout((function() {
        return window.location.reload();
      }), 5 * 60 * 1000);
    }
  });

  source.addEventListener('message', function(e) {
    var data, widget, _i, _len, _ref, _ref1, _ref2, _results;
    data = JSON.parse(e.data);
    if (((_ref = lastEvents[data.id]) != null ? _ref.updatedAt : void 0) !== data.updatedAt) {
      if (Dashing.debugMode) {
        console.log("Received data for " + data.id, data);
      }
      lastEvents[data.id] = data;
      if (((_ref1 = widgets[data.id]) != null ? _ref1.length : void 0) > 0) {
        _ref2 = widgets[data.id];
        _results = [];
        for (_i = 0, _len = _ref2.length; _i < _len; _i++) {
          widget = _ref2[_i];
          _results.push(widget.receiveData(data));
        }
        return _results;
      }
    }
  });

  source.addEventListener('dashboards', function(e) {
    var data;
    data = JSON.parse(e.data);
    if (Dashing.debugMode) {
      console.log("Received data for dashboards", data);
    }
    if (data.dashboard === '*' || window.location.pathname === ("/" + data.dashboard)) {
      return Dashing.fire(data.event, data);
    }
  });

  $(document).ready(function() {
    return Dashing.run();
  });

}).call(this);
