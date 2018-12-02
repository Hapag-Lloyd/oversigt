import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, UrlSegment } from '@angular/router';

@Component({
  selector: 'app-configuration',
  templateUrl: './main.component.html',
  styleUrls: ['./main.component.css']
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
