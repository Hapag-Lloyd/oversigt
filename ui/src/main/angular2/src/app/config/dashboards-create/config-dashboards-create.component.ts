import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { DashboardService, Dashboard } from 'src/oversigt-client';
import { ErrorHandlerService } from 'src/app/services/error-handler.service';
import { getLinkForDashboard } from 'src/app/app.component';
import { ClrLoadingState } from '@clr/angular';
import { UserService } from 'src/app/services/user-service.service';

@Component({
  selector: 'app-config-dashboards-create',
  templateUrl: './config-dashboards-create.component.html',
  styleUrls: ['./config-dashboards-create.component.css']
})
export class ConfigDashboardsCreateComponent implements OnInit {
  private subscriptions: Subscription[] = [];

  dashboardId: string = null;
  dashboard: Dashboard = null;

  creatingDashboardState: ClrLoadingState = ClrLoadingState.DEFAULT;
  enablingDashboardState: ClrLoadingState = ClrLoadingState.DEFAULT;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private dashboardService: DashboardService,
    private errorHandlerService: ErrorHandlerService,
    private userService: UserService,
  ) { }

  ngOnInit() {
    this.subscriptions.push(this.route.url.subscribe(_ => {
      this.initComponent();
    }));
    this.initComponent();
  }

  private initComponent(): void {
    // find selected dashboard id
    this.dashboardId = this.route.snapshot.paramMap.get('dashboardId');

    this.dashboardService.readDashboard(this.dashboardId).subscribe(
      dashboard => {
        this.dashboard = dashboard;
        if (this.dashboard.enabled) {
          this.router.navigateByUrl(getLinkForDashboard(this.dashboardId));
        }
      }, error => {
        if (+error.status === 404) {
          this.dashboard = null;
        } else {
          // TODO: handle this...
        }
      }
    );
  }

  createDashboard(enabled: boolean): void {
    this.creatingDashboardState = ClrLoadingState.LOADING;
    this.dashboardService.createDashboard(this.dashboardId, this.userService.getUserId(), enabled).subscribe(
      ok => {
        this.creatingDashboardState = ClrLoadingState.SUCCESS;
        this.router.navigateByUrl(getLinkForDashboard(this.dashboardId));
      },
      this.errorHandlerService.createErrorHandler('Creating dashboard', () => {
        this.creatingDashboardState = ClrLoadingState.ERROR;
      })
    );
  }

  enableDashboard(): void {
    this.enablingDashboardState = ClrLoadingState.LOADING;
    this.dashboardService.updateDashboardPartially(this.dashboardId, {enabled: true}).subscribe(
      ok => {
        this.enablingDashboardState = ClrLoadingState.SUCCESS;
        this.router.navigateByUrl(getLinkForDashboard(this.dashboardId));
      },
      this.errorHandlerService.createErrorHandler('Updating dashboard', () => {
        this.enablingDashboardState = ClrLoadingState.ERROR;
      })
    );
  }
}
