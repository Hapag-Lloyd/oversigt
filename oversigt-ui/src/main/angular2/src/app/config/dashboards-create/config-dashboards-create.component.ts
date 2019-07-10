import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { DashboardService, Dashboard } from 'src/oversigt-client';
import { ErrorHandlerService } from 'src/app/services/error-handler.service';
import { getLinkForDashboard } from 'src/app/app.component';
import { ClrLoadingState } from '@clr/angular';

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
  requestingDashboardState: ClrLoadingState = ClrLoadingState.DEFAULT;
  enablingDashboardState: ClrLoadingState = ClrLoadingState.DEFAULT;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private dashboardService: DashboardService,
    private errorHandlerService: ErrorHandlerService,
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

  createDashboard(): void {
    // nothing
  }

  requestDashboard(): void {
    // nothing
  }

  enableDashboard(): void {
    // nothing
  }
}
