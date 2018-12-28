import { Component, OnInit, OnDestroy } from '@angular/core';
import { DashboardService, Dashboard, DashboardWidgetService, WidgetInfo, SystemService } from 'src/oversigt-client';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs';
import { FormControl } from '@angular/forms';
import { ClrLoadingState } from '@clr/angular';

@Component({
  selector: 'app-config-dashboards-edit',
  templateUrl: './config-dashboards-edit.component.html',
  styleUrls: ['./config-dashboards-edit.component.scss']
})
export class ConfigDashboardsEditComponent implements OnInit, OnDestroy {
  private subscription: Subscription = null;

  private dashboardId: string = null;
  dashboardTitle = '';
  dashboard: Dashboard = null;
  screensize: number[] = [];
  foregroundColors: string[] = [];
  owners: string[] = [];
  editors: string[] = [];
  widgetInfos: WidgetInfo[] = [];

  // Loading indicator
  saveDashboardState: ClrLoadingState = ClrLoadingState.DEFAULT;

  // for chip editor
  syncUserIdValidators = [];
  asyncUserIdValidators = [];

  constructor(
    private route: ActivatedRoute,
    private dashboardService: DashboardService,
    private widgetService: DashboardWidgetService,
    private systemService: SystemService,
  ) {
    const isUserIdValid = (control: FormControl) => {
      return new Promise(resolve => {
        const userid = control.value;
        systemService.isUserValid(userid).subscribe(valid => {
          resolve(valid);
        },
        error => {
          resolve(false);
        });
      });
    };
    this.asyncUserIdValidators = [isUserIdValid];
    this.syncUserIdValidators = [(control: FormControl) => control.value.length > 0];
  }

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
      this.setDashboard(dashboard);
      // TODO: Error handling
    });
    this.widgetService.listWidgets(this.dashboardId).subscribe(widgetInfos => {
      this.widgetInfos = widgetInfos;
      // TODO: Error handling
    });
  }

  private setDashboard(dashboard: Dashboard, withRights: boolean = false): void {
    this.dashboard = dashboard;
    this.dashboardTitle = dashboard.title;
    this.foregroundColors = [dashboard.foregroundColorStart, dashboard.foregroundColorEnd];
    this.screensize = [dashboard.screenWidth, dashboard.screenHeight];
    if (withRights) {
      this.owners = dashboard.owners;
      this.editors = dashboard.editors;
    }
  }

  saveDashboardSettings(): void {
    this.saveDashboardState = ClrLoadingState.LOADING;
    this.dashboard.foregroundColorStart = this.foregroundColors[0];
    this.dashboard.foregroundColorEnd   = this.foregroundColors[1];
    this.dashboard.screenWidth  = this.screensize[0];
    this.dashboard.screenHeight = this.screensize[1];
    this.dashboardService.updateDashboard(this.dashboardId, this.dashboard).subscribe(dashboard => {
      this.setDashboard(dashboard);
      this.saveDashboardState = ClrLoadingState.SUCCESS;
    },
    error => {
      this.saveDashboardState = ClrLoadingState.ERROR;
      // TODO: Error handling
      alert(error);
      console.log(error);
    });
  }

  countColumns(): number {
    return Math.max(this.dashboard.columns, Math.max(...this.widgetInfos.map(i => i.posX + i.sizeX)) - 1);
  }

  countRows(): number {
    return Math.max(...this.widgetInfos.map(i => i.posY + i.sizeY)) - 1;
  }
}
