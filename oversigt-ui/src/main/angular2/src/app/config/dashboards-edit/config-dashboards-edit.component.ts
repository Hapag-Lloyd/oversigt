import { Component, OnInit, OnDestroy } from '@angular/core';
import { DashboardService, Dashboard, DashboardWidgetService, WidgetInfo } from 'src/oversigt-client';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs';

@Component({
  selector: 'app-config-dashboards-edit',
  templateUrl: './config-dashboards-edit.component.html',
  styleUrls: ['./config-dashboards-edit.component.scss']
})
export class ConfigDashboardsEditComponent implements OnInit, OnDestroy {
  private subscription: Subscription = null;

  private dashboardId: string = null;
  dashboard: Dashboard = null;
  widgetInfos: WidgetInfo[] = [];

  constructor(
    private route: ActivatedRoute,
    private dashboardService: DashboardService,
    private widgetService: DashboardWidgetService,
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
    // find selected dashboard id
    this.dashboardId = this.route.snapshot.paramMap.get('dashboardId');

    this.dashboardService.readDashboard(this.dashboardId).subscribe(dashboard => {
      this.dashboard = dashboard;
      // TODO: Error handling
    });
    this.widgetService.listWidgets(this.dashboardId).subscribe(widgetInfos => {
      this.widgetInfos = widgetInfos;
      // TODO: Error handling
    });
  }

  countColumns(): number {
    return Math.max(this.dashboard.columns, Math.max(...this.widgetInfos.map(i => i.posX + i.sizeX)) - 1);
  }

  countRows(): number {
    return Math.max(...this.widgetInfos.map(i => i.posY + i.sizeY)) - 1;
  }
}
