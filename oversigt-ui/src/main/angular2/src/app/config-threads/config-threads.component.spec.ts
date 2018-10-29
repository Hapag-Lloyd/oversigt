import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { ConfigThreadsComponent } from './config-threads.component';

describe('ConfigThreadsComponent', () => {
  let component: ConfigThreadsComponent;
  let fixture: ComponentFixture<ConfigThreadsComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ ConfigThreadsComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(ConfigThreadsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
