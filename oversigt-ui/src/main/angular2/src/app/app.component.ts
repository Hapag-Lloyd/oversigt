import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { UserService } from './user-service.service';
import { PropertiesService } from './properties-service.service';

export class MenuItem {
  title: string;
  link: string;
  children: MenuItem[];
}

export const MENU_ITEMS: MenuItem[] = [
  { title: 'Event Sources', link: '/config/eventSources', children: [
    /*{ title: 'Create Event Source', link: '/config/eventSources/create', children: []},
    { title: 'Configure Event Sources', link: '/config/eventSources/list', children: []},*/
  ]},
  { title: 'Dashboards', link: '/config/dashboards', children: []},
  { title: 'Properties', link: '/config/properties', children: []},
  { title: 'System', link: '/config/system', children: [
    { title: 'Events', link: '/config/system/events', children: []},
    { title: 'Log Files', link: '/config/system/logfiles', children: []},
    { title: 'Loggers', link: '/config/system/loggers', children: []},
    { title: 'Threads', link: '/config/system/threads', children: []},
    { title: 'Server', link: '/config/system/server', children: []},
  ]},
];

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
