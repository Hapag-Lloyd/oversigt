import { Component, OnInit } from '@angular/core';
import { DashboardService, DashboardInfo } from 'src/oversigt-client';

@Component({
  selector: 'app-config-dashboards',
  templateUrl: './config-dashboards.component.html',
  styleUrls: ['./config-dashboards.component.css']
})
export class ConfigDashboardsComponent implements OnInit {
  dashboards: DashboardInfo[] = [];
  dashboardFilter = '';

  constructor(
    private ds: DashboardService,
  ) { }

  ngOnInit() {
    this.loadDashboards();
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
