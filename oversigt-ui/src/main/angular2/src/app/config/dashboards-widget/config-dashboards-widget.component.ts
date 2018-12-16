import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs';
// tslint:disable-next-line:max-line-length
import { DashboardWidgetService, WidgetDetails, EventSourceService, EventSourceDescriptor, FullEventSourceInstanceInfo } from 'src/oversigt-client';
import { NotificationService } from 'src/app/notification.service';
import { ClrLoadingState } from '@clr/angular';

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

  saveButtonState: ClrLoadingState = ClrLoadingState.DEFAULT;

  constructor(
    private route: ActivatedRoute,
    private dashboardWidgetService: DashboardWidgetService,
    private eventSourceService: EventSourceService,
    private notificationService: NotificationService,
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
      this.setWidgetDetails(widget);
    },
    error => {
      alert(error);
      // TODO: error handling
    });
  }

  private setWidgetDetails(widget: WidgetDetails): void {
    this.widget = widget;
    this.widgetSize = [widget.sizeX, widget.sizeY];
    this.widgetPosition = [widget.posX, widget.posY];
    this.eventSourceService.readInstance(widget.eventSourceInstanceId).subscribe(esi => {
      this.eventSourceInstanceInfo = esi;
      this.eventSourceService.getEventSourceDetails(esi.instanceDetails.eventSourceDescriptor).subscribe(esd => {
        this.eventSourceDescriptor = esd;
        this.saveButtonState = ClrLoadingState.SUCCESS;
      },
      error => {
        console.log(error);
        // TODO: error handling
        this.saveButtonState = ClrLoadingState.ERROR;
      });
    },
    error => {
      console.log(error);
      // TODO: error handling
      this.saveButtonState = ClrLoadingState.ERROR;
    });
  }

  hasEventSourceProperty(propertyName: string): boolean {
    return this.eventSourceInstanceInfo.instanceDetails.dataItems[propertyName] !== undefined;
  }

  saveConfiguration(): void {
    this.saveButtonState = ClrLoadingState.LOADING;
    this.widget.posX = this.widgetPosition[0];
    this.widget.posY = this.widgetPosition[1];
    this.widget.sizeX = this.widgetSize[0];
    this.widget.sizeY = this.widgetSize[1];
    this.dashboardWidgetService.updateWidget(this.dashboardId, this.widgetId, this.widget).subscribe(widget => {
      this.setWidgetDetails(widget);
      this.notificationService.success('Widget ' + this.widget.name + ' has been saved.');
    },
    error => {
      console.log(error);
      // TODO: error handling
      this.saveButtonState = ClrLoadingState.ERROR;
    });
  }
}
