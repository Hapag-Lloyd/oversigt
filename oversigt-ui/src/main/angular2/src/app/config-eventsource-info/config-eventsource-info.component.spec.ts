import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { ConfigEventsourceInfoComponent } from './config-eventsource-info.component';

describe('ConfigEventsourceInfoComponent', () => {
  let component: ConfigEventsourceInfoComponent;
  let fixture: ComponentFixture<ConfigEventsourceInfoComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ ConfigEventsourceInfoComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(ConfigEventsourceInfoComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
