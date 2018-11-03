import { Component, OnInit, Input } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { SerializableValueService } from 'src/oversigt-client';
import { UserService } from '../../user-service.service';

class MenuItem {
  label: string;
  icon?: string;
  routerLink?: string;
  items?: MenuItem[] = [];
  requiredRole?: string;
}

@Component({
  selector: 'app-config-menu',
  templateUrl: './config-menu.component.html',
  styleUrls: ['./config-menu.component.css']
})
export class ConfigMenuComponent implements OnInit {
  @Input() isCollapsed: boolean;
  private items: MenuItem[];

  constructor(
    private route: ActivatedRoute,
    private userService: UserService,
    private router: Router,
    private sps: SerializableValueService,
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
            {label: 'Create', icon: 'plus-circle', routerLink: '/config/createDashboard', requiredRole: 'server.admin'},
            {label: 'Configure', icon: 'bars', routerLink: '/config/dashboards'} ]
      }, {
        label: 'Properties', icon: 'appstore', requiredRole: 'server.dashboard.owner',
        items: []
      }, {
        label: 'System', icon: 'desktop',
        items: [
            {label: 'Logs', icon: 'exception', routerLink: '/config/logfiles'},
            {label: 'Loggers', icon: 'filter', routerLink: '/config/loggers', requiredRole: 'server.admin'},
            {label: 'Events', icon: 'thunderbolt', routerLink: '/config/events'},
            {label: 'Threads', icon: 'interation', routerLink: '/config/threads'},
            {label: 'System', icon: 'hdd', routerLink: '/config/system', requiredRole: 'server.admin'} ]
      }
    ];
    this.sps.listPropertyTypes().subscribe(
      types => this.items.find(item => item.label === 'Properties').items = types
          .map(type => <MenuItem>{label: type,
                                  icon: 'build',
                                  routerLink: '/config/properties/' + type,
                                  requiredRole: 'server.dashboard.owner', })
          .sort((a, b) => a.label.toLowerCase() > b.label.toLowerCase() ? 1 : -1)
    );
  }

  get itemsForUser(): MenuItem[] {
    return this.items;
  }

  doLogout() {
    this.userService.logOut();
    this.router.navigateByUrl('/');
  }
}
