import { Component, OnInit } from '@angular/core';
import { MenuItem } from 'src/app/app.component';
import { ActivatedRoute, Router } from '@angular/router';

@Component({
  selector: 'app-config-list-childcomponents',
  templateUrl: './config-list-childcomponents.component.html',
  styleUrls: ['./config-list-childcomponents.component.css']
})
export class ConfigListChildcomponentsComponent implements OnInit {
  menuitem: MenuItem = undefined;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
  ) { }

  ngOnInit() {
    const url = this.route.snapshot.url.map(s => s.path).join('/');
    console.log(url);
  }

}
