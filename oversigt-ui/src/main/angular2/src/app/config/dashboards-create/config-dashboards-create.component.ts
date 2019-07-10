import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs';

@Component({
  selector: 'app-config-dashboards-create',
  templateUrl: './config-dashboards-create.component.html',
  styleUrls: ['./config-dashboards-create.component.css']
})
export class ConfigDashboardsCreateComponent implements OnInit {
  private subscriptions: Subscription[] = [];

  dashboardId: string = null;

  constructor(
    private route: ActivatedRoute,
  ) { }

  ngOnInit() {
    this.subscriptions.push(this.route.url.subscribe(_ => {
      this.initComponent();
    }));
  }

  private initComponent(): void {
    // find selected dashboard id
    this.dashboardId = this.route.snapshot.paramMap.get('dashboardId');

    // TODO: check if dashboard already exists!
  }
}
