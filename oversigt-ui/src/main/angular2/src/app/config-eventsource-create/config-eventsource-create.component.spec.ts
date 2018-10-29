import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { ConfigEventsourceCreateComponent } from './config-eventsource-create.component';

describe('ConfigEventsourceCreateComponent', () => {
  let component: ConfigEventsourceCreateComponent;
  let fixture: ComponentFixture<ConfigEventsourceCreateComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ ConfigEventsourceCreateComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(ConfigEventsourceCreateComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
