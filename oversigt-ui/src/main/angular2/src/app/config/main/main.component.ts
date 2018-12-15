import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { MENU_ITEMS, MenuItem } from 'src/app/app.component';

@Component({
  selector: 'app-configuration',
  templateUrl: './main.component.html',
  styleUrls: ['./main.component.css']
})
export class ConfigurationComponent implements OnInit {
  private menuItems = MENU_ITEMS;
  menuItem: MenuItem = undefined;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
  ) { }

  ngOnInit() {
    const loadedUrl = this.route.snapshot.pathFromRoot.map(p => p.url.map(u => u.path).join('/')).join('/').replace('//', '/');
    this.menuItem = this.menuItems.find(mi => {
      return mi.link === loadedUrl;
    });
  }

  hasSelectedChild(): boolean {
    return this.route.snapshot.children.length > 0;
  }
}
