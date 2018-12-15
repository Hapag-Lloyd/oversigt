import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs';
// tslint:disable-next-line:max-line-length
import { DashboardWidgetService, WidgetDetails, EventSourceService, EventSourceDescriptor, FullEventSourceInstanceInfo } from 'src/oversigt-client';

@Component({
  selector: 'app-config-dashboard-widget',
  templateUrl: './config-dashboards-widget.component.html',
  styleUrls: ['./config-dashboards-widget.component.css']
})
export class ConfigDashboardWidgetComponent implements OnInit, OnDestroy {
  private subscription: Subscription = null;
  private dashboardId: string = null;
  private widgetId: number = null;
  widget: WidgetDetails = null;
  eventSourceInstanceInfo: FullEventSourceInstanceInfo = null;
  eventSourceDescriptor: EventSourceDescriptor = null;
  widgetSize: number[] = null;
  widgetPosition: number[] = null;

  constructor(
    private route: ActivatedRoute,
    private dashboardWidgetService: DashboardWidgetService,
    private eventSourceService: EventSourceService,
  ) { }

  ngOnInit() {
    this.subscription = this.route.url.subscribe(_ => {
      this.initComponent();
    });
  }

  ngOnDestroy() {
    if (this.subscription !== null) {
      this.subscription.unsubscribe();
    }
  }

  private initComponent(): void {
    // find selected dashboard and widget id
    this.dashboardId = this.route.snapshot.parent.paramMap.get('dashboardId');
    this.widgetId = +this.route.snapshot.paramMap.get('widgetId');

    this.widget = null; // TODO: show loading
    this.eventSourceDescriptor = null;
    this.dashboardWidgetService.readWidget(this.dashboardId, this.widgetId, true).subscribe(widget => {
      this.widget = widget;
      this.widgetSize = [widget.sizeX, widget.sizeY];
      this.widgetPosition = [widget.posX, widget.posY];
      this.eventSourceService.readInstance(widget.eventSourceInstanceId).subscribe(esi => {
        this.eventSourceInstanceInfo = esi;
        this.eventSourceService.getEventSourceDetails(esi.instanceDetails.eventSourceDescriptor).subscribe(esd => {
          this.eventSourceDescriptor = esd;
        }); // TODO: error handling
      } // TODO: error handling
      );
    },
    error => {
      alert(error);
      // TODO: error handling
    });
  }

  hasEventSourceProperty(propertyName: string): boolean {
    return this.eventSourceInstanceInfo.instanceDetails.dataItems[propertyName] !== undefined;
  }
}
