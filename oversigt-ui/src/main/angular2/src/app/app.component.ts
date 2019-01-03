import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { UserService } from './user-service.service';
import { PropertiesService } from './properties-service.service';

export class MenuItem {
  id: string;
  title: string;
  link: string;
  children?: MenuItem[] = [];
  neededRole?: string;
}

const PREFIX = '';

export const MENU_ITEMS: MenuItem[] = [
  { title: 'Event Sources', link: PREFIX + '/eventSources', id: 'eventsources', children: [
    /*{ title: 'Create Event Source', link: PREFIX + '/eventSources/create', children: []},
    { title: 'Configure Event Sources', link: PREFIX + '/eventSources/list', children: []},*/
  ]},
  { title: 'Dashboards', link: PREFIX + '/dashboards', id: 'dashboards', children: []},
  { title: 'Properties', link: PREFIX + '/properties', id: 'properties'},
  { title: 'System', link: PREFIX + '/system', id: 'system', children: [
    { title: 'Events', link: PREFIX + '/system/events', id: 'system-events', children: []},
    { title: 'Log Files', link: PREFIX + '/system/logfiles', id: 'system-logfiles', children: []},
    { title: 'Loggers', link: PREFIX + '/system/loggers', id: 'system-loggers', children: []},
    { title: 'Threads', link: PREFIX + '/system/threads', id: 'system-threads', children: []},
    { title: 'Server', link: PREFIX + '/system/server', id: 'system-server', children: []},
  ]},
];

export function getLinkForId(id: string): string {
  return MENU_ITEMS.find(mi => mi.id === id).link;
}

export function getLinkForProperty(id: string): string {
  return getLinkForId('properties') + '/' + id;
}

export function getLinkForEventSource(id: string): string {
  return getLinkForId('eventsources') + '/' + id;
}

export function getLinkForDashboards(): string {
  return getLinkForId('dashboards');
}

export function getLinkForDashboard(dashboardId: string): string {
  return getLinkForDashboards() + '/' + dashboardId;
}

export function getLinkForDashboardWidget(dashboardId: string, widgetId: number): string {
  return getLinkForDashboard(dashboardId) + '/' + String(widgetId);
}

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css']
})
export class AppComponent implements OnInit {
  title = 'oversigt-ui';

  constructor(
  ) {
  }

  ngOnInit(): void {
  }
}
