import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { MENU_ITEMS, MenuItem, getLinkForProperty } from 'src/app/app.component';
import { UserService } from 'src/app/services/user-service.service';
import { PropertiesService } from 'src/app/services/properties-service.service';

@Component({
  selector: 'app-configuration',
  templateUrl: './main.component.html',
  styleUrls: ['./main.component.css']
})
export class ConfigurationComponent implements OnInit {
  menuItem: MenuItem = undefined;

  menuItems = MENU_ITEMS;
  isCollapsed: boolean;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private userService: UserService,
    private propertiesService: PropertiesService,
  ) { }

  ngOnInit() {
    this.isCollapsed = false;
    this.propertiesService.loadProperties(props => {
      const item = this.menuItems.find(mi => mi.id === 'properties');
      item.children = props.map(p => <MenuItem>{title: p.name, link: getLinkForProperty(p.name), children: [], description: p.description});
    });
    const loadedUrl = this.route.snapshot.pathFromRoot.map(p => p.url.map(u => u.path).join('/')).join('/').replace('//', '/');
    this.menuItem = this.menuItems.find(mi => {
      return mi.link === loadedUrl;
    });
  }

  getUserName(): string {
    return this.userService.getName();
  }

  hasSelectedChild(): boolean {
    return this.route.snapshot.children.length > 0;
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
    this.router.navigateByUrl('/login');
  }
}
