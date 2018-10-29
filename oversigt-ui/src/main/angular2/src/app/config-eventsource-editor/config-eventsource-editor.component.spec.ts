import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { ConfigEventsourceEditorComponent } from './config-eventsource-editor.component';

describe('ConfigEventsourceEditorComponent', () => {
  let component: ConfigEventsourceEditorComponent;
  let fixture: ComponentFixture<ConfigEventsourceEditorComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ ConfigEventsourceEditorComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(ConfigEventsourceEditorComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
