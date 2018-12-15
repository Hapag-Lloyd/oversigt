import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs';
import { DashboardWidgetService, WidgetDetails } from 'src/oversigt-client';

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

  constructor(
    private route: ActivatedRoute,
    private dashboardWidgetService: DashboardWidgetService,
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

    this.widget = null;
    this.dashboardWidgetService.readWidget(this.dashboardId, this.widgetId, true).subscribe(widget => {
      this.widget = widget;
    },
    error => {
      alert(error);
      // TODO: error handling
    });
  }
}
