import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Configuration,
  DashboardService, DashboardWidgetService, Dashboard,
  ViewService,
  WidgetDetails,
  WidgetShortInfo} from '../../oversigt-client';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { Observable } from 'rxjs/Observable';
import * as $ from 'jquery';

const TILE_DISTANCE = 5;

@Component({
  selector: 'app-dashboard',
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.scss']
})
export class DashboardComponent implements OnInit {
  public state = 'NOT_LOADED';
  public dashboardInfo: Dashboard = null;
  public widgets: Widget[] = [];

  public tileBaseWidth = 1;
  public tileBaseHeight = 1;

  constructor(
    public sanitizer: DomSanitizer,
    private configuration: Configuration,
    private ds: DashboardService,
    private dws: DashboardWidgetService,
    private vs: ViewService,
    private route: ActivatedRoute
  ) {}

  ngOnInit() {
    // path to API
    this.configuration.basePath = 'http://localhost/api/v1'; // just for documentation
    const eventSourceUrl = 'http://localhost/events';

    // read basic dashboard information from API
    const dashboardId = this.route.snapshot.paramMap.get('dashboardId');
    this.ds.readDashboard(dashboardId).subscribe(
      dashboardInfo => {
        this.dashboardInfo = dashboardInfo;
        // compute widget dimensions
        this.tileBaseWidth = Math.round(
          (dashboardInfo.screenWidth - TILE_DISTANCE * dashboardInfo.columns) /
            dashboardInfo.columns
        );
        const height = dashboardInfo.screenHeight / (this.tileBaseWidth - 2);
        this.tileBaseHeight = Math.round(
          (dashboardInfo.screenHeight - TILE_DISTANCE * height) / height
        );
        // OK, show dashboard and continue
        this.state = 'SHOW_DASHBOARD';
      },
      error => {
        // something failed while loading the dashboard info
        if (error.status === 404) {
          this.state = 'CREATE_DASHBOARD';
        } else {
          this.state = 'ERROR';
        }
        console.log(error);
      }
    );

    // list widgets of current dashboard
    this.dws.listWidgets(dashboardId).subscribe(widgetInfos => {
      this.widgets = [];
      // for each widget: load detailed information
      widgetInfos.forEach(widgetInfo =>
        this.loadWidgetStuff(dashboardId, widgetInfo, w => this.initWidget(w))
      );
    });

    // start reading events from server
    const observable = Observable.create(observer => {
      const eventSource = new EventSource(eventSourceUrl);
      eventSource.onmessage = x => observer.next(x.data);
      eventSource.onerror = x => observer.error(x);

      return eventSource.close;
    });

    // handle events from server
    observable.subscribe({
      next: data => {
        console.log(data);
      },
      error: err => console.error('something wrong occurred: ' + err)
    });
  }

  private loadWidgetStuff(
    dashboardId: string,
    widgetInfo: WidgetShortInfo,
    callback: (Widget) => void
  ): void {
    // Load generic widget data
    this.dws.readWidget(dashboardId, widgetInfo.id).subscribe(widgetDetails => {
      if (!widgetDetails.enabled) {
        // disabled widgets should not be shown
        return;
      }
      const widget = new Widget(
        this,
        widgetInfo.id,
        widgetDetails,
        widgetInfo.view
      );

      // read the HTML for the widget
      this.vs.readHtml(widget.view).subscribe(html => {
        // widget.html = this.sanitizer.bypassSecurityTrustHtml(html);
        widget.html = html;

        // read the CSS for the widget
        this.vs.readCss(widget.view).subscribe(css => {
          // widget.css = this.sanitizer.bypassSecurityTrustStyle(css);
          widget.css = css;

          // read the JavaScript for the widget
          this.vs.readJavascript(widget.view).subscribe(script => {
            // widget.script = this.sanitizer.bypassSecurityTrustScript(script);
            widget.script = script;

            // resolve the promise
            callback(widget);
          });
        });
      });
    });
  }

  private initWidget(widget: Widget): void {
    this.widgets.push(widget);
    setTimeout(() => {
      const element = $('#' + widget.details.eventSourceInstanceId);
      console.log(element);
      // alert(widget.details.eventSourceInstanceId);
    }, 0);
  }
}

class Widget {
  dashboard: DashboardComponent;
  id: number;
  view: string;
  details: WidgetDetails;
  html?: string /*SafeHtml*/;
  css?: string /*SafeStyle*/;
  script?: string /*SafeScript*/;

  constructor(
    dashboard: DashboardComponent,
    id: number,
    details: WidgetDetails,
    view: string
  ) {
    this.dashboard = dashboard;
    this.id = id;
    this.details = details;
    this.view = view;
  }

  getX(): number {
    return (
      TILE_DISTANCE * this.details.posX +
      (this.details.posX - 1) * this.dashboard.tileBaseWidth
    );
  }

  getY(): number {
    return (
      TILE_DISTANCE * this.details.posY +
      (this.details.posY - 1) * this.dashboard.tileBaseHeight
    );
  }

  getWidth(): number {
    return (
      this.details.sizeX * this.dashboard.tileBaseWidth +
      TILE_DISTANCE * (this.details.sizeX - 1)
    );
  }

  getHeight(): number {
    return (
      this.details.sizeY * this.dashboard.tileBaseHeight +
      TILE_DISTANCE * (this.details.sizeY - 1)
    );
  }

  getInnerHtml(): SafeHtml {
    const html =
      '<style type="text/css">' +
      this.css +
      '</style>' +
      this.html +
      '<script type="text/javascript">' +
      this.script +
      '</script>';
    return this.dashboard.sanitizer.bypassSecurityTrustHtml(this.html);
  }

  getInnerCss(): SafeHtml {
    return this.dashboard.sanitizer.bypassSecurityTrustHtml(
      '<style type="text/css">' + this.css + '</style>'
    );
  }

  getInnerJs(): SafeHtml {
    return this.dashboard.sanitizer.bypassSecurityTrustHtml(
      '<script type="text/javascript">' + this.script + '</script>'
    );
  }

  getClass(): string {
    const s = this.view.replace(/([A-Z])/g, $1 => '-' + $1.toLowerCase());
    if (s.startsWith('-')) {
      return s.substring(1);
    } else {
      return s;
    }
  }
}
