import { Component, OnInit, OnDestroy } from '@angular/core';
import { DashboardService, DashboardInfo } from 'src/oversigt-client';
import { Subscription } from 'rxjs';
import { ActivatedRoute, Router } from '@angular/router';
import { UserService } from 'src/app/services/user-service.service';
import { NotificationService } from 'src/app/services/notification.service';
import { getLinkForDashboard } from 'src/app/app.component';

@Component({
  selector: 'app-config-dashboards',
  templateUrl: './config-dashboards.component.html',
  styleUrls: ['./config-dashboards.component.css']
})
export class ConfigDashboardsComponent implements OnInit, OnDestroy {
  private subscription: Subscription = null;

  dashboards: DashboardInfo[] = [];
  dashboardFilter = '';

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private ds: DashboardService,
    private userService: UserService,
    private notification: NotificationService,
  ) { }

  ngOnInit() {
    this.subscription = this.route.url.subscribe(_ => {
      this.loadDashboards();
    });
  }

  ngOnDestroy() {
    if (this.subscription) {
      this.subscription.unsubscribe();
    }
  }

  private loadDashboards(): void {
    this.ds.listDashboardIds().subscribe(
      list => {
        this.dashboards = list.sort((a, b) => a.title.toLowerCase() > b.title.toLowerCase() ? 1 : -1);
      },
      error => {
        console.error(error);
        alert(error);
        // TODO: Error handling
      }
    );
  }

  createDashboard(id: string) {
    if (id.trim().length === 0) {
      this.notification.warning('The ID you entered was empty. Cannot create dashboard.');
      return;
    }
    this.ds.createDashboard(id, this.userService.getName(), this.userService.hasRole('server.admin')).subscribe(
      dashboard => {
        this.notification.success('Dashboard "' + dashboard.id + '" has been created.');
        this.router.navigateByUrl(getLinkForDashboard(dashboard.id));
      }, error => {
        // TODO: error handling
        console.log(error);
        alert(error);
      }
    );
  }
}
