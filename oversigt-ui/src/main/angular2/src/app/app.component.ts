import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { UserService } from './user-service.service';
import { PropertiesService } from './properties-service.service';

export class MenuItem {
  id: string;
  title: string;
  link: string;
  children: MenuItem[];
}

export const MENU_ITEMS: MenuItem[] = [
  { title: 'Event Sources', link: '/config/eventSources', id: 'eventsources', children: [
    /*{ title: 'Create Event Source', link: '/config/eventSources/create', children: []},
    { title: 'Configure Event Sources', link: '/config/eventSources/list', children: []},*/
  ]},
  { title: 'Dashboards', link: '/config/dashboards', id: 'dashboards', children: []},
  { title: 'Properties', link: '/config/properties', id: 'properties', children: []},
  { title: 'System', link: '/config/system', id: 'system', children: [
    { title: 'Events', link: '/config/system/events', id: 'system-events', children: []},
    { title: 'Log Files', link: '/config/system/logfiles', id: 'system-logfiles', children: []},
    { title: 'Loggers', link: '/config/system/loggers', id: 'system-loggers', children: []},
    { title: 'Threads', link: '/config/system/threads', id: 'system-threads', children: []},
    { title: 'Server', link: '/config/system/server', id: 'system-server', children: []},
  ]},
];

export function getLinkForId(id: string): string {
  return MENU_ITEMS.find(mi => mi.id === id).link;
}

export function getLinkForEventSource(id: string): string {
  return getLinkForId('eventsources') + '/' + id;
}

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css']
})
export class AppComponent implements OnInit {
  title = 'oversigt-ui';
  isCollapsed: boolean;

  menuItems = MENU_ITEMS;

  constructor(
    private router: Router,
    private userService: UserService,
    private propertiesService: PropertiesService,
  ) {
  }

  ngOnInit(): void {
    this.isCollapsed = false;
    this.propertiesService.loadProperties(props => {
      const item = this.menuItems.find(mi => mi.title === 'Properties');
      item.children = props.map(p => <MenuItem>{title: p, link: '/config/properties/' + p, children: []});
    });
  }

  isShowingConfiguration(): boolean {
    const url = this.router.url;
    return url.startsWith('/config/') || url === '/config';
  }

  getSelectedMenuItem(): MenuItem {
    const url = this.router.url;
    const item = this.menuItems.find(c => url.startsWith(c.link));
    if (item !== undefined) {
      return item;
    } else {
      return null;
    }
  }

  getSelectedMenuItemLink(): string {
    const item = this.getSelectedMenuItem();
    return item !== null ? item.link : '';
    }

  getSelectedMenuItemChildren(): MenuItem[] {
    const item = this.getSelectedMenuItem();
    return item !== null ? item.children : [];
  }

  getSelectedSubMenuItemLink(): string {
    const url = this.router.url;
    const item = this.getSelectedMenuItem().children.find(c => c.link === url);
    return item !== undefined ? item.link : '';
  }

  doLogout() {
    this.userService.logOut();
    this.router.navigateByUrl('/');
  }
}
