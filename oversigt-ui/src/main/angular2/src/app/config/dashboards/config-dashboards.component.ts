import { Component, OnInit, OnDestroy } from '@angular/core';
import { DashboardService, DashboardInfo } from 'src/oversigt-client';
import { Subscription } from 'rxjs';
import { ActivatedRoute } from '@angular/router';

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
    private ds: DashboardService,
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
}
