import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, UrlSegment } from '@angular/router';
import { debug } from 'util';

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
  templateUrl: './configuration.component.html',
  styleUrls: ['./configuration.component.css']
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
