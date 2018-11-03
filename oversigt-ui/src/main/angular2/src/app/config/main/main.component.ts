import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, UrlSegment } from '@angular/router';

export enum ConfigurationSelection {
  None = '',
  EventSources = 'eventSources',
  Dashboards = 'dashboards',
  Properties = 'properties',
  System = 'system',
  Logs = 'logs'
}

@Component({
  selector: 'app-configuration',
  templateUrl: './main.component.html',
  styleUrls: ['./main.component.css']
})
export class ConfigurationComponent implements OnInit {

  constructor(
    private route: ActivatedRoute
  ) { }

  ngOnInit() {
  }

  hasSelectedChild(): boolean {
    return this.route.snapshot.children.length > 0;
  }
}
