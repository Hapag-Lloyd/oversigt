import { Component, OnInit, Input } from '@angular/core';

export class Item {
  id: string;
  title: string;
  value: string;
  readonly: boolean;
}

@Component({
  selector: 'app-config-eventsource-info',
  templateUrl: './config-eventsource-info.component.html',
  styleUrls: ['./config-eventsource-info.component.css']
})
export class ConfigEventsourceInfoComponent implements OnInit {
  @Input() item: Item;

  constructor() { }

  ngOnInit() {
  }

}
