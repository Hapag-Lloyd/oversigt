import { Component, OnInit, Input } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { SerializableValueService } from 'src/oversigt-client';

class MenuItem {
  label: string;
  icon?: string;
  routerLink?: string;
  items?: MenuItem[];
}

@Component({
  selector: 'app-config-menu',
  templateUrl: './config-menu.component.html',
  styleUrls: ['./config-menu.component.css']
})
export class ConfigMenuComponent implements OnInit {
  @Input() isCollapsed: boolean;
  items: MenuItem[];

  constructor(
    private route: ActivatedRoute,
    private sps: SerializableValueService
  ) { }

  ngOnInit() {
    const segment = this.route.snapshot.url[1];
    const path = segment != null ? segment.path : '';
    // this.currentMenuSelection = this.menuItems.filter(mi => mi.link === path)[0];
    this.items = [
      { label: 'Event Sources', icon: 'info-circle',
        items: [
          {label: 'New', icon: 'plus-circle', routerLink: '/config/createEventSource'},
          {label: 'Configure', icon: 'edit', routerLink: '/config/eventSources'} ]
      }, {
        label: 'Dashboards', icon: 'fund',
        items: [
            {label: 'Create', icon: 'plus-circle', routerLink: '/config/createDashboard'},
            {label: 'Configure', icon: 'bars', routerLink: '/config/dashboards'} ]
      }, {
        label: 'Properties', icon: 'appstore',
        items: []
      }, {
        label: 'System', icon: 'desktop',
        items: [
            {label: 'Logs', icon: 'exception', routerLink: '/config/logfiles'},
            {label: 'Loggers', icon: 'filter', routerLink: '/config/loggers'},
            {label: 'Events', icon: 'thunderbolt', routerLink: '/config/events'},
            {label: 'Threads', icon: 'interation', routerLink: '/config/threads'},
            {label: 'System', icon: 'hdd', routerLink: '/config/system'} ]
      }
    ];
    this.sps.listPropertyTypes().subscribe(
      types => this.items[2].items = types
          .map(type => this.createMenuItem(type, '/config/properties/' + type))
          .sort((a, b) => a.label.toLowerCase() > b.label.toLowerCase() ? 1 : -1)
    );
  }

  private createMenuItem(label: string, routerLink: string): MenuItem {
    return {label: label, routerLink: routerLink, icon: 'build'};
  }

}
