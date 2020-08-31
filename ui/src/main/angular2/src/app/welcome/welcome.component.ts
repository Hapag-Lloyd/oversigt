import { Component, OnInit } from '@angular/core';
import { DashboardService, DashboardInfo } from '../../oversigt-client';

@Component({
  selector: 'app-welcome',
  templateUrl: './welcome.component.html',
  styleUrls: ['./welcome.component.css']
})
export class WelcomeComponent implements OnInit {
  dashboards = [];

  constructor(private ds: DashboardService) {
  }

  ngOnInit() {
    this.ds.listDashboardIds().subscribe((dashboardInfos) => {
      this.dashboards = dashboardInfos.sort((a, b) => (a.title.trim().toLowerCase() < b.title.trim().toLowerCase() ? -1 : 1));
    });
  }
}
