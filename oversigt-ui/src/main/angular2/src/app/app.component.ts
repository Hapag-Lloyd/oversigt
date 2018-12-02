import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { UserService } from './user-service.service';

export class MenuItem {
  title: string;
  link: string;
  children: MenuItem[];
}

const MENU_ITEMS: MenuItem[] = [
  { title: 'Event Sources', link: '/config/eventSources', children: [
    { title: 'Create Event Source', link: '/config/eventSources/create', children: []},
    { title: 'Configure Event Sources', link: '/config/eventSources', children: []},
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
  ) {
  }

  ngOnInit(): void {
    this.isCollapsed = false;
  }

  isShowingConfiguration(): boolean {
    const url = this.router.url;
    return url.startsWith('/config/') || url === '/config';
  }

  getSelectedMenuItemChildren(): MenuItem[] {
    const url = this.router.url;
    const item = this.menuItems.find(c => url.startsWith(c.link));
    if (item !== undefined) {
      return item.children;
    } else {
      return [];
    }
  }

  doLogout() {
    this.userService.logOut();
    this.router.navigateByUrl('/');
  }
}
