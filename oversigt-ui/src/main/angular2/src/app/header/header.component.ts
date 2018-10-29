import { Component, OnInit, Input, TemplateRef, ViewChild, ViewContainerRef } from '@angular/core';

@Component({
  selector: 'app-header',
  templateUrl: './header.component.html',
  styleUrls: ['./header.component.css']
})
export class HeaderComponent implements OnInit {
  @Input() level = 2;
  @Input() title: string;
  @Input() additional: TemplateRef<void>;

  constructor( ) { }

  ngOnInit() {
  }

}
